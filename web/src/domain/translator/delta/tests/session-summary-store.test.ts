import { expect, it, vi } from 'vitest';
import { createDeltaSession } from '@auto-novel/translator';
import { openDB } from 'idb';
import { IndexedDbDeltaSessionStore } from '../IndexedDbDeltaSessionStore';
import { summarizeDeltaSession } from '../session-summary';

vi.mock('idb', () => ({ openDB: vi.fn() }));

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
