import { afterEach, expect, it, vi } from 'vitest';
import {
  createDeltaSession,
  type DeltaChapterSession,
} from '@auto-novel/translator';
import { openDB } from 'idb';
import { IndexedDbDeltaSessionStore } from '../IndexedDbDeltaSessionStore';
import { summarizeDeltaSession } from '../session-summary';
import { THINKING_RETENTION_MS } from '../thinking-retention';

vi.mock('idb', () => ({ openDB: vi.fn() }));
afterEach(() => vi.restoreAllMocks());

function records() {
  return ['a', 'b'].map((chapterId) =>
    createDeltaSession({
      runId: 'r',
      workerId: 'w',
      identity: { taskId: 't', novelId: 'n', chapterId },
      source: ['large source'],
      glossary: {},
      glossaryId: 'g',
      model: {
        protocol: 'openai-completions',
        endpoint: 'https://test.invalid',
        model: 'm',
      },
    }),
  );
}

it('lists summaries from the index without touching full session records', async () => {
  const sessions = records();
  const summaries = sessions.map(summarizeDeltaSession);
  const getAll = vi.fn(async () => summaries);
  const transaction = vi.fn();
  vi.mocked(openDB).mockResolvedValue({
    getAll,
    transaction,
  } as never);
  const store = new IndexedDbDeltaSessionStore('summary-test');
  const results = await store.listSummaries();
  expect(openDB).toHaveBeenCalledWith('summary-test', 1, expect.any(Object));
  expect(getAll).toHaveBeenCalledWith('summaries');
  expect(transaction).not.toHaveBeenCalled();
  expect(results.map((item) => item.identity.chapterId)).toEqual(['a', 'b']);
  expect(
    results.every((item) => !('source' in item) && !('messages' in item)),
  ).toBe(true);
});

// Simulate the synchronous snapshot made by native IDBObjectStore.put. Keep
// its clone separate from the application-level clone we want to eliminate.
function database(previous?: DeltaChapterSession) {
  const nativeClone = structuredClone;
  let persisted: DeltaChapterSession | undefined;
  let indexed: ReturnType<typeof summarizeDeltaSession> | undefined;
  const sessions = {
    get: vi.fn(async () => previous),
    put: vi.fn((session: DeltaChapterSession) => {
      persisted = nativeClone(session);
      return Promise.resolve(session.id);
    }),
  };
  const summaries = {
    get: vi.fn(async () => previous && summarizeDeltaSession(previous)),
    put: vi.fn((summary: ReturnType<typeof summarizeDeltaSession>) => {
      indexed = nativeClone(summary);
      return Promise.resolve(summary.id);
    }),
  };
  const tx = {
    objectStore: (name: string) => (name === 'sessions' ? sessions : summaries),
    abort: vi.fn(),
    done: Promise.resolve(),
  };
  const transaction = vi.fn(() => tx);
  vi.mocked(openDB).mockResolvedValue({ transaction } as never);
  return {
    store: new IndexedDbDeltaSessionStore('save-test'),
    sessions,
    summaries,
    transaction,
    tx,
    persisted: () => persisted!,
    indexed: () => indexed!,
  };
}

it('checks revisions using only the summary and lets IndexedDB snapshot the transcript', async () => {
  const [previous] = records();
  const next = { ...previous, revision: previous.revision + 1 };
  const db = database(previous);
  const clone = vi.spyOn(globalThis, 'structuredClone');
  await db.store.save(next);
  expect(db.summaries.get).toHaveBeenCalledWith(next.id);
  expect(db.sessions.get).not.toHaveBeenCalled();
  expect(clone).not.toHaveBeenCalled();
  expect(db.transaction).toHaveBeenCalledWith(
    ['sessions', 'summaries'],
    'readwrite',
  );
  next.source[0] = 'caller changed after save';
  expect(db.persisted().source).toEqual(['large source']);
  expect(db.indexed().revision).toBe(next.revision);
});

it('rejects stale revisions before writing either record', async () => {
  const [previous] = records();
  const db = database(previous);
  await expect(db.store.save(previous)).rejects.toThrow('版本冲突');
  expect(db.tx.abort).toHaveBeenCalledOnce();
  expect(db.sessions.put).not.toHaveBeenCalled();
  expect(db.summaries.put).not.toHaveBeenCalled();
});

function addDiagnostics(session: DeltaChapterSession, expired: boolean) {
  const timestamp = expired
    ? Date.now() - THINKING_RETENTION_MS - 1000
    : Date.now();
  const message = {
    role: 'assistant',
    content: [
      { type: 'thinking', thinking: 'large thinking'.repeat(1000) },
      { type: 'toolCall', id: 'call', name: 'submit_batch', arguments: {} },
    ],
    timestamp,
  } as NonNullable<DeltaChapterSession['pendingAssistant']>['message'];
  // Only diagnostic fields are relevant to retention; protocol/usage are opaque.
  const pending = {
    requestId: 'request',
    message,
    usagePresence: {},
  } as unknown as NonNullable<DeltaChapterSession['pendingAssistant']>;
  session.messages = [pending.message];
  session.pendingAssistant = pending;
  session.metadata.description = 'description';
  session.review = {
    messages: [pending.message],
    pendingAssistant: pending,
    originalTranslations: { p0: 'before', p1: 'unchanged' },
    changes: [{ paragraphId: 'p0', translation: 'after' }],
  } as unknown as NonNullable<DeltaChapterSession['review']>;
}

it('prunes expired pending thinking without mutating the caller or review input', async () => {
  const [session] = records();
  addDiagnostics(session, true);
  const original = structuredClone(session);
  const db = database();
  await db.store.save(session);
  expect(session).toEqual(original);
  expect(db.persisted().messages[0]).toMatchObject({
    content: [{ type: 'toolCall' }],
  });
  expect(db.persisted().pendingAssistant!.message.content).toHaveLength(1);
  expect(db.persisted().review!.messages[0]).toMatchObject({
    content: [{ type: 'toolCall' }],
  });
  expect(db.persisted().review!.pendingAssistant!.message.content).toHaveLength(
    1,
  );
});

it('compacts expired completed diagnostics without deleting caller metadata or originals', async () => {
  const [session] = records();
  addDiagnostics(session, true);
  session.phase = 'completed';
  session.completedAt = Date.now() - THINKING_RETENTION_MS - 1000;
  const original = structuredClone(session);
  const db = database();
  await db.store.save(session);
  expect(session).toEqual(original);
  expect(db.persisted().messages).toEqual([]);
  expect(db.persisted().metadata.description).toBeUndefined();
  expect(db.persisted().review!.originalTranslations).toEqual({ p0: 'before' });
  expect(db.indexed()).toMatchObject({
    phase: 'completed',
    revision: session.revision,
  });
});

it('preserves recent thinking and replay history by reference until native put snapshots them', async () => {
  const [session] = records();
  addDiagnostics(session, false);
  const db = database();
  await db.store.save(session);
  const written = db.sessions.put.mock.calls[0][0];
  expect(written.messages).toBe(session.messages);
  expect(written.review!.messages).toBe(session.review!.messages);
  expect(db.persisted()).toEqual(session);
});
