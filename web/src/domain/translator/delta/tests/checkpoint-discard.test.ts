/* eslint-disable compat/compat -- Node-only test harness. */
import { describe, expect, it, vi } from 'vitest';
import {
  createDeltaSession,
  type DeltaChapterSession,
} from '@auto-novel/translator';
import { DeltaController } from '../DeltaController';
import {
  summarizeDeltaSession,
  type DeltaSessionSummary,
} from '../session-summary';
import type { DeltaSessionRepository, DeltaTaskSource } from '../types';

function checkpoint(novelId: string, chapterId: string, volumeId?: string) {
  const session = createDeltaSession({
    runId: `old-${chapterId}`,
    workerId: 'worker',
    identity: { taskId: novelId, novelId, chapterId, volumeId },
    source: ['原文'],
    glossaryId: 'g1',
    glossary: {},
    model: {
      endpoint: 'https://model.test',
      model: 'model',
      protocol: 'openai-completions',
      requestParams: {},
    },
  });
  session.pauseReason = 'paused';
  session.translations = { p000001: '草稿' };
  return session;
}

function fixture(records: DeltaChapterSession[]) {
  const saved = new Map(records.map((s) => [s.id, structuredClone(s)]));
  const store: DeltaSessionRepository = {
    get: async (id) => structuredClone(saved.get(id)),
    list: async () => structuredClone([...saved.values()]),
    listSummaries: async () => [...saved.values()].map(summarizeDeltaSession),
    save: async (s) => {
      saved.set(s.id, structuredClone(s));
    },
    delete: vi.fn(async (id) => {
      saved.delete(id);
    }),
    clear: vi.fn(async () => {
      saved.clear();
    }),
  };
  const uploaded = new Map<string, string[]>();
  const src: DeltaTaskSource = {
    readChapterContext: async () => ({
      available: false,
      reason: 'no context',
    }),
    novelId: 'novel-a',
    metadata: {},
    chapters: [{ chapterId: '1', title: '1', order: 0, status: 'pending' }],
    readChapter: vi.fn(async (id) => ({
      paragraphs: ['原文'],
      glossary: {},
      glossaryId: 'g1',
      oldParagraphZh: uploaded.get(id) ?? null,
    })),
    uploadChapter: vi.fn(async (id, _glossary, text) => {
      uploaded.set(id, text);
    }),
  };
  let sessions: DeltaSessionSummary[] = [];
  const change = vi.fn();
  const run = vi.fn(async (s: DeltaChapterSession) => ({
    ...s,
    revision: s.revision + 1,
    pauseReason: undefined,
    phase: 'readyToSubmit' as const,
    finalParagraphs: ['正式译文'],
    finalHash: 'final',
  }));
  const controller = new DeltaController({
    store,
    source: async () => src,
    jobs: () => [{ task: 'novel-a', description: 'Novel A', createAt: 1 }],
    onSessionEvent: (event) => {
      if (event.type === 'reset') sessions = event.sessions;
      else if (event.type === 'upsert')
        sessions = [
          ...sessions.filter((s) => s.id !== event.session.id),
          event.session,
        ];
      else sessions = sessions.filter((s) => !event.ids.includes(s.id));
    },
    onChapterChange: change,
    runChapter: run,
  });
  return {
    controller,
    store,
    saved,
    src,
    uploaded,
    change,
    run,
    sessions: () => sessions,
  };
}

describe('Delta scoped checkpoint discard', () => {
  it('discards exactly one chapter without affecting sibling drafts or other novels', async () => {
    const records = [
      checkpoint('novel-a', '1'),
      checkpoint('novel-a', '2'),
      checkpoint('novel-b', '1'),
    ];
    const f = fixture(records);
    await f.controller.load();
    await f.controller.discardChapter(records[0].id);
    expect(await f.store.list()).toEqual(records.slice(1));
    expect(f.sessions()).toEqual(records.slice(1).map(summarizeDeltaSession));
    expect(f.change.mock.calls).toEqual([['novel-a', '1', 'pending']]);
    expect(f.store.clear).not.toHaveBeenCalled();
    expect(f.src.readChapter).not.toHaveBeenCalled();
    expect(f.src.uploadChapter).not.toHaveBeenCalled();
    await f.controller.load();
    expect(f.sessions()).toEqual(records.slice(1).map(summarizeDeltaSession));
  });

  it('discards the selected novel across volumes while preserving every other novel', async () => {
    const records = [
      checkpoint('novel-a', '1', 'v1'),
      checkpoint('novel-a', '1', 'v2'),
      checkpoint('novel-b', '1', 'v1'),
    ];
    records[0].phase = 'completed';
    records[0].pauseReason = undefined;
    const f = fixture(records);
    f.uploaded.set('1', ['已提交的译文']);
    await f.controller.load();
    await f.controller.discardNovel('novel-a');
    expect(await f.store.list()).toEqual([records[2]]);
    expect(f.sessions()).toEqual([summarizeDeltaSession(records[2])]);
    expect(f.uploaded.get('1')).toEqual(['已提交的译文']);
    expect(f.src.uploadChapter).not.toHaveBeenCalled();
    expect(f.store.clear).not.toHaveBeenCalled();
    expect(f.change).toHaveBeenCalledTimes(1);
  });

  it('creates a fresh chapter session after discard without rewriting another worker draft', async () => {
    const records = [checkpoint('novel-a', '1'), checkpoint('novel-b', '1')];
    records[1].workerId = 'other';
    const f = fixture(records);
    await f.controller.load();
    await f.controller.discardChapter(records[0].id);
    await f.controller.startWorker({
      id: 'worker',
      concurrency: 1,
      endpoint: 'https://model.test',
      model: 'model',
      key: 'secret',
    });
    expect(f.run).toHaveBeenCalledTimes(1);
    expect(f.run.mock.calls[0][0].runId).not.toBe(records[0].runId);
    expect(f.run.mock.calls[0][0].translations).toEqual({});
    expect(await f.store.get(records[1].id)).toEqual(records[1]);
    expect((await f.store.get(records[0].id))?.phase).toBe('completed');
  });

  it('rejects discard during execution without stopping workers or deleting checkpoints', async () => {
    const f = fixture([checkpoint('novel-a', '1')]);
    await f.controller.load();
    let release!: () => void;
    f.run.mockImplementation(async (s) => {
      await new Promise<void>((resolve) => {
        release = resolve;
      });
      return {
        ...s,
        revision: s.revision + 1,
        phase: 'readyToSubmit',
        pauseReason: undefined,
        finalParagraphs: ['正式译文'],
        finalHash: 'final',
      };
    });
    const running = f.controller.startWorker({
      id: 'worker',
      concurrency: 1,
      endpoint: 'https://model.test',
      model: 'model',
      key: 'secret',
    });
    await vi.waitFor(() => expect(release).toBeDefined());
    await expect(f.controller.discardNovel('novel-a')).rejects.toThrow(
      '正在运行',
    );
    expect(f.store.delete).not.toHaveBeenCalled();
    expect(f.controller.isRunning('worker')).toBe(true);
    release();
    await running;
  });

  it('keeps remaining checkpoints visible if a novel deletion fails halfway', async () => {
    const records = [
      checkpoint('novel-a', '1'),
      checkpoint('novel-a', '2'),
      checkpoint('novel-b', '1'),
    ];
    const f = fixture(records);
    const remove = f.store.delete;
    f.store.delete = vi.fn(async (id) => {
      if (id === records[1].id) throw new Error('storage failed');
      await remove(id);
    });
    await f.controller.load();
    await expect(f.controller.discardNovel('novel-a')).rejects.toThrow(
      'storage failed',
    );
    expect(await f.store.list()).toEqual(records.slice(1));
    expect(f.sessions()).toEqual(records.slice(1).map(summarizeDeltaSession));
  });
});
