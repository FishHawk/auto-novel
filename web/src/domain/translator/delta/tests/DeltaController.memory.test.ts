/* eslint-disable compat/compat -- Node-only test harness. */
import { describe, expect, it, vi } from 'vitest';
import {
  createDeltaSession,
  deltaFingerprint,
  type DeltaChapterSession,
} from '@auto-novel/translator';
import { DeltaController, deltaWorkerModel } from '../DeltaController';
import {
  summarizeDeltaSession,
  type DeltaSessionEvent,
} from '../session-summary';

const worker = {
  id: 'worker',
  concurrency: 2,
  endpoint: 'https://model.test',
  model: 'model',
  key: 'test-only',
};
function chapter(chapterId: string) {
  return createDeltaSession({
    runId: chapterId,
    workerId: worker.id,
    identity: { taskId: 'task', taskCreatedAt: 1, novelId: 'novel', chapterId },
    source: ['原文'],
    glossaryId: 'g',
    glossary: {},
    model: deltaWorkerModel(worker),
    previousTranslationHash: deltaFingerprint({
      paragraphs: null,
      glossary: {},
    }),
  });
}
function fixture(
  records: DeltaChapterSession[] = [],
  options: Partial<ConstructorParameters<typeof DeltaController>[0]> = {},
) {
  const saved = new Map(records.map((s) => [s.id, structuredClone(s)]));
  const store = {
    list: vi.fn(async () => structuredClone([...saved.values()])),
    listSummaries: vi.fn(async () =>
      [...saved.values()].map(summarizeDeltaSession),
    ),
    get: vi.fn(async (id: string) => structuredClone(saved.get(id))),
    save: vi.fn(async (s: DeltaChapterSession) => {
      saved.set(s.id, structuredClone(s));
    }),
    delete: vi.fn(async (id: string) => {
      saved.delete(id);
    }),
    clear: vi.fn(async () => {
      saved.clear();
    }),
  };
  const events: DeltaSessionEvent[] = [];
  const runChapter = vi.fn(async (s: DeltaChapterSession) => ({
    ...s,
    revision: s.revision + 1,
    phase: 'readyToSubmit' as const,
    pauseReason: undefined,
    finalParagraphs: ['译文'],
    finalHash: 'final',
  }));
  const controller = new DeltaController({
    store,
    jobs: () => [{ task: 'task', description: 'Novel', createAt: 1 }],
    source: async () => ({
      novelId: 'novel',
      metadata: {},
      chapters: [],
      readChapter: async () => ({
        paragraphs: ['原文'],
        glossaryId: 'g',
        glossary: {},
        oldParagraphZh: null,
      }),
      readChapterContext: async () => ({
        available: false,
        reason: 'no context',
      }),
      uploadChapter: vi.fn(async () => {}),
    }),
    runChapter,
    onSessionEvent: (event: DeltaSessionEvent) => events.push(event),
    ...options,
  });
  return { controller, store, events, runChapter, saved };
}

describe('Delta controller bounded session history', () => {
  it('retranslates only the selected task with the chosen model and fresh history', async () => {
    const selectedWorker = { ...worker, model: 'another-model' };
    const old = { ...chapter('a'), phase: 'completed' as const };
    old.messages = [{ role: 'user', content: 'old history', timestamp: 0 }];
    const other = chapter('b');
    other.identity.taskId = 'other';
    const onTaskDone = vi.fn();
    const f = fixture([old, other], {
      jobs: () => [
        { task: 'other', description: 'Other', createAt: 1 },
        { task: 'task', description: 'Novel', createAt: 2 },
      ],
      source: async () => ({
        novelId: 'novel',
        metadata: {},
        chapters: [{ chapterId: 'a', title: 'A', order: 0, status: 'pending' }],
        readChapter: async () => ({
          paragraphs: ['原文'],
          glossaryId: 'g',
          glossary: {},
          oldParagraphZh: null,
        }),
        readChapterContext: async () => ({
          available: false,
          reason: 'no context',
        }),
        uploadChapter: vi.fn(),
      }),
      onTaskDone,
    });
    await f.controller.load();

    await f.controller.startWorker(selectedWorker, 'task');

    expect(f.runChapter).toHaveBeenCalledOnce();
    const session = f.runChapter.mock.calls[0][0];
    expect(session.model.model).toBe('another-model');
    expect(session.runId).not.toBe(old.runId);
    expect(session.messages).toEqual([]);
    expect((await f.store.get(old.id))?.phase).toBe('completed');
    expect(await f.store.get(other.id)).toEqual(other);
    expect(onTaskDone.mock.calls).toEqual([['task', 1]]);
  });

  it('loads summaries without fetching any complete chapter histories', async () => {
    const f = fixture([chapter('a'), chapter('b')]);
    await f.controller.load();
    expect(f.store.list).not.toHaveBeenCalled();
    expect(f.store.listSummaries).toHaveBeenCalledOnce();
    expect(f.store.get).not.toHaveBeenCalled();
    expect(f.events).toEqual([{ type: 'reset', sessions: expect.any(Array) }]);
    expect(JSON.stringify(f.events)).not.toContain('messages');
  });

  it('publishes only the changed summary and keeps stored history available on demand', async () => {
    const a = chapter('a');
    const b = { ...chapter('b'), phase: 'completed' as const };
    const f = fixture([a, b]);
    await f.controller.load();
    f.events.length = 0;
    await f.controller.startWorker(worker);
    expect(f.events.length).toBeGreaterThan(0);
    expect(
      f.events.every((e) => e.type === 'upsert' && e.session.id === a.id),
    ).toBe(true);
    expect(JSON.stringify(f.events)).not.toContain('messages');
    expect(f.store.get.mock.calls.some(([id]) => id === b.id)).toBe(false);
    const detail = await f.controller.getSession(a.id);
    expect(detail?.phase).toBe('completed');
    expect(detail?.messages).toBeDefined();
  });

  it('reserves a checkpoint before its asynchronous detail read', async () => {
    const a = chapter('a');
    const f = fixture([a]);
    let release!: () => void;
    const gate = new Promise<void>((resolve) => {
      release = resolve;
    });
    const get = f.store.get.getMockImplementation()!;
    f.store.get.mockImplementation(async (id) => {
      await gate;
      return get(id);
    });
    await f.controller.load();
    const running = f.controller.startWorker(worker);
    await vi.waitFor(() => expect(f.store.get).toHaveBeenCalledOnce());
    expect(f.controller.hasTask('task')).toBe(true);
    release();
    await running;
    expect(f.runChapter).toHaveBeenCalledOnce();
    expect(f.controller.hasTask('task')).toBe(false);
  });

  it('removes only successfully deleted summaries if a later deletion fails', async () => {
    const a = chapter('a');
    const b = chapter('b');
    const f = fixture([a, b]);
    await f.controller.load();
    f.events.length = 0;
    const remove = f.store.delete.getMockImplementation()!;
    f.store.delete.mockImplementation(async (id) => {
      if (id === b.id) throw new Error('storage failure');
      await remove(id);
    });
    await expect(f.controller.discardPending()).rejects.toThrow(
      'storage failure',
    );
    expect(f.events).toEqual([{ type: 'remove', ids: [a.id] }]);
    expect(f.saved.has(b.id)).toBe(true);
  });

  it('does not publish a successful update when persistence fails', async () => {
    const f = fixture([chapter('a')]);
    await f.controller.load();
    f.events.length = 0;
    f.store.save.mockRejectedValue(new Error('disk full'));
    await expect(f.controller.startWorker(worker)).rejects.toThrow('disk full');
    expect(f.events).toEqual([]);
    expect(f.controller.hasTask('task')).toBe(false);
  });
});

describe('Delta output simplification', () => {
  it('simplifies finished translations before fingerprinting and upload', async () => {
    const uploadChapter = vi.fn(async () => {});
    const f = fixture([chapter('a')], {
      source: async () => ({
        novelId: 'novel',
        metadata: {},
        chapters: [],
        readChapter: async () => ({
          paragraphs: ['原文'],
          glossaryId: 'g',
          glossary: {},
          oldParagraphZh: null,
        }),
        readChapterContext: async () => ({
          available: false,
          reason: 'no context',
        }),
        uploadChapter,
      }),
      simplifyText: async (text) =>
        text.replaceAll('繁', '简').replaceAll('體', '体'),
      runChapter: async (s) => ({
        ...s,
        revision: s.revision + 1,
        phase: 'readyToSubmit' as const,
        pauseReason: undefined,
        translations: { p000001: '繁體' },
        finalParagraphs: ['繁體'],
        finalHash: deltaFingerprint(['繁體']),
      }),
    });
    await f.controller.load();

    await f.controller.startWorker(worker);

    expect(uploadChapter).toHaveBeenCalledWith('a', 'g', ['简体']);
    const saved = await f.store.get(chapter('a').id);
    expect(saved?.translations).toEqual({ p000001: '简体' });
    expect(saved?.finalParagraphs).toEqual(['简体']);
    expect(saved?.phase).toBe('completed');
  });

  it('uploads the original output when the converter is unavailable', async () => {
    const uploadChapter = vi.fn(async () => {});
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {});
    const f = fixture([chapter('a')], {
      source: async () => ({
        novelId: 'novel',
        metadata: {},
        chapters: [],
        readChapter: async () => ({
          paragraphs: ['原文'],
          glossaryId: 'g',
          glossary: {},
          oldParagraphZh: null,
        }),
        readChapterContext: async () => ({
          available: false,
          reason: 'no context',
        }),
        uploadChapter,
      }),
      simplifyText: () => {
        throw new Error('opencc unavailable');
      },
      runChapter: async (s) => ({
        ...s,
        revision: s.revision + 1,
        phase: 'readyToSubmit' as const,
        pauseReason: undefined,
        translations: { p000001: '繁體' },
        finalParagraphs: ['繁體'],
        finalHash: deltaFingerprint(['繁體']),
      }),
    });
    await f.controller.load();

    await f.controller.startWorker(worker);

    expect(uploadChapter).toHaveBeenCalledWith('a', 'g', ['繁體']);
    expect(warn).toHaveBeenCalled();
    warn.mockRestore();
  });
});

describe('Delta task scope', () => {
  it('skips chapters excluded from the job', async () => {
    const f = fixture([], {
      jobs: () => [
        {
          task: 'task',
          description: 'Novel',
          createAt: 1,
          excludedChapters: ['a'],
        },
      ],
      source: async () => ({
        novelId: 'novel',
        metadata: {},
        chapters: [
          { chapterId: 'a', title: 'A', order: 0, status: 'pending' },
          { chapterId: 'b', title: 'B', order: 1, status: 'pending' },
        ],
        readChapter: async (chapterId: string) => ({
          paragraphs: [chapterId],
          glossaryId: 'g',
          glossary: {},
          oldParagraphZh: null,
        }),
        readChapterContext: async () => ({
          available: false,
          reason: 'no context',
        }),
        uploadChapter: vi.fn(),
      }),
    });
    await f.controller.load();
    await f.controller.startWorker(worker);
    expect(f.runChapter).toHaveBeenCalledOnce();
    expect(f.runChapter.mock.calls[0][0].identity.chapterId).toBe('b');
  });

  it('removes all checkpoints for one task without touching other tasks', async () => {
    const a = { ...chapter('a'), phase: 'completed' as const };
    const b = chapter('b');
    const other = {
      ...chapter('c'),
      identity: { ...chapter('c').identity, taskId: 'other' },
    };
    const f = fixture([a, b, other]);
    await f.controller.load();
    f.events.length = 0;
    await f.controller.discardTask('task');
    expect(f.saved.has(a.id)).toBe(false);
    expect(f.saved.has(b.id)).toBe(false);
    expect(f.saved.has(other.id)).toBe(true);
    expect(f.events).toContainEqual({
      type: 'remove',
      ids: expect.arrayContaining([a.id, b.id]),
    });
  });

  it('removes one chapter checkpoint by task and chapter id', async () => {
    const a = chapter('a');
    const b = chapter('b');
    const f = fixture([a, b]);
    await f.controller.load();
    await f.controller.discardChapterByTask('task', 'a');
    expect(f.saved.has(a.id)).toBe(false);
    expect(f.saved.has(b.id)).toBe(true);
  });
});
