import { describe, expect, it } from 'vitest';
import {
  createDeltaSession,
  type DeltaChapterSession,
  type DeltaRequestRecord,
} from '@auto-novel/translator';
import * as summaries from '../session-summary';

function session(): DeltaChapterSession {
  return createDeltaSession({
    runId: 'run',
    workerId: 'worker',
    identity: {
      taskId: 'task',
      novelId: 'novel',
      chapterId: 'chapter',
      title: 'Chapter',
    },
    metadata: {
      title: 'Novel',
      chapterTitle: 'Chapter',
      description: 'large description',
    },
    source: ['first', '', 'third'],
    glossaryId: 'g',
    glossary: {},
    model: {
      protocol: 'openai-completions',
      endpoint: 'https://model.test',
      model: 'm',
      requestParams: { prompt: 'large params' },
    },
    batchMaxParagraphs: 1,
  });
}
function request(
  requestId: string,
  overrides: Partial<DeltaRequestRecord> = {},
): DeltaRequestRecord {
  return {
    requestId,
    batchId: 'b1',
    window: 1,
    attempt: 1,
    status: 'accepted',
    ...overrides,
  };
}
const usage = (
  input: number,
  output: number,
  cacheRead: number,
  cacheWrite: number,
) => ({
  input,
  output,
  cacheRead,
  cacheWrite,
  totalTokens: input + output + cacheRead + cacheWrite,
  cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 },
});

describe('Delta session summaries', () => {
  it('keeps independent display fields and progress without retaining chapter or request payloads', () => {
    const full = session();
    full.cursor = 1;
    full.translations[full.paragraphs[0].id] = 'translation';
    full.retry.totalAttempts = 3;
    full.pauseReason = 'paused';
    full.requests = [
      request('last', { error: 'large error', usage: usage(1, 2, 3, 4) }),
    ];
    const summary = summaries.summarizeDeltaSession(full);
    expect(summary).toEqual({
      id: full.id,
      runId: 'run',
      workerId: 'worker',
      identity: full.identity,
      revision: full.revision,
      phase: 'translating',
      pauseReason: 'paused',
      metadata: { title: 'Novel', chapterTitle: 'Chapter' },
      model: {
        protocol: 'openai-completions',
        endpoint: 'https://model.test',
        model: 'm',
      },
      cursor: 1,
      batchCount: full.batches.length,
      paragraphCount: 3,
      completedParagraphs: 2,
      totalAttempts: 3,
      lastRequest: request('last'),
      requestCount: 1,
      usage: {
        input: { total: 0, reported: 0 },
        output: { total: 0, reported: 0 },
        cacheRead: { total: 0, reported: 0 },
        cacheWrite: { total: 0, reported: 0 },
      },
      tokenTotals: { cacheRead: 3, output: 2 },
      reviewCompleted: false,
    });
    full.identity.title = 'changed';
    full.metadata.title = 'changed';
    full.model.model = 'changed';
    full.requests[0].status = 'error';
    expect(summary.identity.title).toBe('Chapter');
    expect(summary.metadata.title).toBe('Novel');
    expect(summary.model.model).toBe('m');
    expect(summary.lastRequest?.status).toBe('accepted');
    expect(Object.keys(summary)).not.toEqual(
      expect.arrayContaining([
        'source',
        'messages',
        'paragraphs',
        'translations',
        'tools',
        'requests',
        'review',
      ]),
    );
    expect(JSON.stringify(summary)).not.toMatch(
      /large description|large error|large params/,
    );
  });

  it('deduplicates usage by request id while preserving raw task totals and reported zero coverage', () => {
    const full = session();
    full.requests = [
      request('duplicate', {
        usage: usage(999, 50, 20, 10),
        usagePresence: {
          input: true,
          output: true,
          cacheRead: true,
          cacheWrite: true,
        },
      }),
      request('missing'),
      request('duplicate', {
        usage: usage(0, 4, 2, 0),
        usagePresence: {
          input: true,
          output: true,
          cacheRead: false,
          cacheWrite: true,
        },
      }),
      request('incomplete', {
        usagePresence: {
          input: true,
          output: true,
          cacheRead: true,
          cacheWrite: true,
        },
      }),
    ];
    const summary = summaries.summarizeDeltaSession(full);
    expect(summary.requestCount).toBe(3);
    expect(summary.usage).toEqual({
      input: { total: 0, reported: 1 },
      output: { total: 4, reported: 1 },
      cacheRead: { total: 0, reported: 0 },
      cacheWrite: { total: 0, reported: 1 },
    });
    expect(summary.tokenTotals).toEqual({ cacheRead: 22, output: 54 });
    expect(summary.lastRequest?.requestId).toBe('incomplete');
  });

  it('reads Review completion without counting Review requests or retaining review text', () => {
    const full = session();
    full.review = {
      completed: true,
      requests: [request('review', { usage: usage(100, 200, 300, 400) })],
      messages: [{ role: 'user', content: 'large review text' }],
    } as DeltaChapterSession['review'];
    const summary = summaries.summarizeDeltaSession(full);
    expect(summary.reviewCompleted).toBe(true);
    expect(summary.requestCount).toBe(0);
    expect(summary.lastRequest).toBeUndefined();
    expect(summary.tokenTotals).toEqual({ cacheRead: 0, output: 0 });
    expect(JSON.stringify(summary)).not.toContain('large review text');
  });
});
