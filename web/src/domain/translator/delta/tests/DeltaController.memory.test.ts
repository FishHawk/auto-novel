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
function chapter(chapterId: string, source = ['原文']) {
  return createDeltaSession({
    runId: chapterId,
    workerId: worker.id,
    identity: { taskId: 'task', taskCreatedAt: 1, novelId: 'novel', chapterId },
    source,
    glossaryId: 'g',
    glossary: {},
    model: deltaWorkerModel(worker),
    previousTranslationHash: deltaFingerprint({
      paragraphs: null,
      glossary: {},
    }),
  });
}
function completedChapter(chapterId: string) {
  const session = chapter(chapterId, ['原文一', '原文二']);
  session.phase = 'completed';
  session.completedAt = Date.now();
  session.batches = [
    { id: 'b0001', paragraphIds: ['p000001'] },
    { id: 'b0002', paragraphIds: ['p000002'] },
  ];
  session.cursor = 2;
  session.translations = { p000001: '译文一', p000002: '译文二' };
  session.finalParagraphs = ['译文一', '译文二'];
  session.messages = [
    { role: 'user', content: '完整提示与历史', timestamp: Date.now() },
  ];
  session.preferences = '翻译偏好';
  session.glossary = { 原文: '译文' };
  session.requests = [
    {
      requestId: 'r1',
      batchId: 'b0001',
      window: 1,
      attempt: 1,
      status: 'accepted',
      usage: {
        input: 11,
        output: 5,
        cacheRead: 3,
        cacheWrite: 0,
        totalTokens: 19,
        cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 },
      },
      usagePresence: {
        input: true,
        output: true,
        cacheRead: true,
        cacheWrite: false,
      },
    },
  ];
  session.review = {
    id: 'review',
    options: { enabled: true, mode: 'new' },
    draftHash: 'draft',
    originalTranslations: { p000001: '初译一', p000002: '译文二' },
    systemPrompt: '审校提示',
    tools: session.tools,
    messages: [
      { role: 'user', content: '完整审校历史', timestamp: Date.now() },
    ],
    initialMessageCount: 0,
    requests: session.requests,
    attempts: 1,
    failures: 0,
    totalAttempts: 1,
    window: 1,
    contextReads: 1,
    changes: [
      {
        paragraphId: 'p000001',
        translation: '译文一',
        category: 'accuracy',
        reason: '修正语义',
      },
    ],
    unresolved: [],
    completed: true,
  };
  return session;
}
function fixture(
  records: DeltaChapterSession[] = [],
  options: Partial<ConstructorParameters<typeof DeltaController>[0]> = {},
) {
  const saved = new Map(records.map((s) => [s.id, structuredClone(s)]));
  const store = {
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
  it('lets another worker claim and complete a different chapter while the first waits for review', async () => {
    let releaseReview!: () => void;
    const waiting = new Promise<void>((resolve) => {
      releaseReview = resolve;
    });
    const reviewed: string[] = [];
    const upload = vi.fn();
    const f = fixture([], {
      source: async () => ({
        novelId: 'novel',
        metadata: {},
        chapters: ['a', 'b'].map((chapterId, order) => ({
          chapterId,
          title: chapterId,
          order,
          status: 'pending',
        })),
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
        uploadChapter: upload,
      }),
      runReview: async (session) => {
        reviewed.push(session.identity.chapterId);
        if (session.identity.chapterId === 'a') await waiting;
        return {
          ...session,
          revision: session.revision + 1,
          phase: 'readyToSubmit',
        };
      },
    });
    const first = {
      ...worker,
      id: 'first',
      concurrency: 1,
      deltaReview: { enabled: true, mode: 'new' as const },
    };
    const second = { ...first, id: 'second' };
    await f.controller.load();
    const firstRun = f.controller.startWorker(first);
    try {
      await vi.waitFor(() => expect(reviewed).toEqual(['a']));
      await f.controller.startWorker(second);
      expect(f.controller.isRunning(first.id)).toBe(true);
      expect(f.saved.get(chapter('a').id)?.phase).toBe('reviewing');
      expect(f.saved.get(chapter('b').id)?.phase).toBe('completed');
      expect(
        f.runChapter.mock.calls.map(([session]) => [
          session.identity.chapterId,
          session.workerId,
        ]),
      ).toEqual([
        ['a', 'first'],
        ['b', 'second'],
      ]);
      expect(upload.mock.calls.map(([chapterId]) => chapterId)).toEqual(['b']);
    } finally {
      releaseReview();
      await firstRun;
    }
  });

  it('loads summaries without fetching any complete chapter histories', async () => {
    const f = fixture([chapter('a'), chapter('b')]);
    await f.controller.load();
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

  it('pauses without uploading when simplification fails', async () => {
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

    expect(uploadChapter).not.toHaveBeenCalled();
    const saved = await f.store.get(chapter('a').id);
    expect(saved?.phase).toBe('readyToSubmit');
    expect(saved?.pauseReason).toContain('opencc unavailable');
    expect(saved?.finalParagraphs).toEqual(['繁體']);
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

  it('removes task drafts but retains compact completed results without touching other tasks', async () => {
    const a = completedChapter('a');
    const b = chapter('b');
    const other = {
      ...chapter('c'),
      identity: { ...chapter('c').identity, taskId: 'other' },
    };
    const f = fixture([a, b, other]);
    await f.controller.load();
    f.events.length = 0;
    await f.controller.discardTask('task');
    const retained = f.saved.get(a.id)!;
    expect(retained).toMatchObject({
      revision: a.revision + 1,
      phase: 'completed',
      model: a.model,
      paragraphs: a.paragraphs,
      batches: a.batches,
      translations: a.translations,
      requests: a.requests,
      source: [],
      messages: [],
      systemPrompt: '',
      tools: [],
      glossary: {},
      preferences: '',
      review: {
        completed: true,
        requests: a.review!.requests,
        changes: a.review!.changes,
        originalTranslations: { p000001: '初译一' },
        messages: [],
        systemPrompt: '',
        tools: [],
      },
    });
    expect(retained.finalParagraphs).toBeUndefined();
    expect(f.saved.has(b.id)).toBe(false);
    expect(f.saved.has(other.id)).toBe(true);
    expect(f.events).toContainEqual({
      type: 'remove',
      ids: [b.id],
    });
    expect(f.events).toContainEqual({
      type: 'upsert',
      session: summarizeDeltaSession(retained),
    });
    expect(
      f.events.some(
        (event) => event.type === 'remove' && event.ids.includes(a.id),
      ),
    ).toBe(false);

    f.store.get.mockClear();
    await f.controller.load();
    expect(f.store.get).not.toHaveBeenCalled();
    expect(await f.controller.getSession(a.id)).toEqual(retained);
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

  it('retains completed chapter batches when removing just that chapter from its task', async () => {
    const a = completedChapter('a');
    const b = chapter('b');
    const f = fixture([a, b]);
    await f.controller.load();
    await f.controller.discardChapterByTask('task', 'a');
    expect(f.saved.get(a.id)?.batches).toEqual(a.batches);
    expect(f.saved.get(a.id)?.messages).toEqual([]);
    expect(f.saved.get(b.id)).toEqual(b);
  });

  it('keeps completed history and its summary intact if compacting fails', async () => {
    const a = completedChapter('a');
    const f = fixture([a]);
    await f.controller.load();
    f.events.length = 0;
    f.store.save.mockRejectedValueOnce(new Error('storage failed'));
    await expect(f.controller.discardTask('task')).rejects.toThrow(
      'storage failed',
    );
    expect(f.saved.get(a.id)).toEqual(a);
    expect(f.events).toEqual([]);
  });

  it('still deletes completed results when explicitly clearing chapter data', async () => {
    const a = completedChapter('a');
    const f = fixture([a]);
    await f.controller.load();
    await f.controller.discardChapter(a.id);
    expect(f.saved.has(a.id)).toBe(false);
  });

  it('starts a fresh translation after removing and readding a task with retained results', async () => {
    const a = completedChapter('a');
    let createAt = 1;
    const f = fixture([a], {
      jobs: () => [{ task: 'task', description: 'Novel', createAt }],
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
    });
    await f.controller.load();
    await f.controller.discardTask('task');
    expect(f.saved.has(a.id)).toBe(true);
    createAt = 2;
    await f.controller.startWorker(worker);
    expect(f.runChapter).toHaveBeenCalledOnce();
    const fresh = f.runChapter.mock.calls[0][0];
    expect(fresh.runId).not.toBe(a.runId);
    expect(fresh.identity.taskCreatedAt).toBe(2);
    expect(fresh.translations).toEqual({});
  });
});
