import { describe, expect, it } from 'vitest';
import type { AssistantMessage, ToolCall } from '@earendil-works/pi-ai';
import {
  createDeltaSession,
  runDeltaChapter,
} from '../src/delta/chapter-runner';
import type { DeltaChapterSession } from '../src/delta/types';
import {
  normalizeDeltaReviewOptions,
  runDeltaReview,
} from '../src/delta/review-runner';
import type { PiAiSessionRequest } from '../src/delta/pi-ai-transport';

const presence = {
  input: true,
  output: true,
  cacheRead: false,
  cacheWrite: false,
};

function assistant(name: string, args: unknown, id = 'call'): AssistantMessage {
  return {
    role: 'assistant',
    api: 'openai-completions',
    provider: 'test',
    model: 'model',
    timestamp: 1,
    stopReason: 'toolUse',
    content: [{ type: 'toolCall', id, name, arguments: args } as ToolCall],
    usage: {
      input: 10,
      output: 3,
      cacheRead: 0,
      cacheWrite: 0,
      totalTokens: 13,
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 },
    },
  };
}

function store() {
  const snapshots: DeltaChapterSession[] = [];
  return {
    snapshots,
    async save(session: DeltaChapterSession) {
      snapshots.push(structuredClone(session));
    },
  };
}

function createReviewSession(mode: 'new' | 'append' = 'new', variant?: string) {
  return createDeltaSession({
    runId: 'run',
    workerId: 'worker',
    identity: { taskId: 'task', novelId: 'novel', chapterId: 'chapter' },
    source: ['　口調は厳しい。', '', '素早さ：1'],
    glossaryId: 'g',
    glossary: { 厳しい: '严厉' },
    metadata: { title: '小说', description: '简介', chapterTitle: '第3章' },
    model: {
      protocol: 'openai-completions',
      endpoint: 'https://test.example/v1',
      model: 'model',
    },
    reviewOptions: {
      enabled: true,
      mode,
      ...(variant ? { variant } : {}),
    } as DeltaChapterSession['reviewOptions'],
  });
}

async function translate(session: DeltaChapterSession) {
  return runDeltaChapter(session, {
    key: 'secret',
    store: store(),
    transport: async () => ({
      message: assistant('submit_batch', {
        batchId: 'b0001',
        entries: [
          { paragraphId: 'p000001', translation: '口调很严厉。' },
          { paragraphId: 'p000003', translation: '素早さ：1' },
        ],
      }),
      usagePresence: presence,
    }),
  });
}

const change = {
  paragraphId: 'p000001',
  translation: '语气很严厉。',
  category: 'fluency',
  reason: '此处口調指说话语气。',
};

describe('ReviewX-only review pipeline', () => {
  it('normalizes legacy review variants to ReviewX and drops the option', () => {
    expect(
      normalizeDeltaReviewOptions({
        enabled: true,
        mode: 'new',
        variant: 'review',
      } as never),
    ).toEqual({ enabled: true, mode: 'new' });
  });

  it('applies a ReviewX submission and completes the chapter', async () => {
    const translated = await translate(createReviewSession());
    const reviewStore = store();
    const result = await runDeltaReview(translated, {
      key: 'secret',
      store: reviewStore,
      transport: async () => {
        const snapshot = reviewStore.snapshots.at(-1)!;
        return {
          message: assistant('submit_review', {
            reviewId: snapshot.review!.id,
            draftHash: snapshot.review!.draftHash,
            changes: [change],
            unresolved: [],
          }),
          usagePresence: presence,
        };
      },
    });
    expect(result.phase).toBe('readyToSubmit');
    expect(result.review!.completed).toBe(true);
    expect(result.review!.options).toEqual({
      enabled: true,
      mode: 'new',
    });
    expect(result.finalParagraphs).toEqual(['　语气很严厉。', '', '素早さ：1']);
  });

  it('resumes a legacy review checkpoint with ReviewX', async () => {
    const translated = await translate(createReviewSession('new', 'review'));
    const reviewStore = store();
    const result = await runDeltaReview(translated, {
      key: 'secret',
      store: reviewStore,
      transport: async () => {
        const snapshot = reviewStore.snapshots.at(-1)!;
        return {
          message: assistant('submit_review', {
            reviewId: snapshot.review!.id,
            draftHash: snapshot.review!.draftHash,
            changes: [],
            unresolved: [],
          }),
          usagePresence: presence,
        };
      },
    });
    expect(result.phase).toBe('readyToSubmit');
    expect(result.review!.options).toEqual({
      enabled: true,
      mode: 'new',
    });
  });
});

describe('DeltaX fresh-context translation', () => {
  function contextSession(deltaMode: 'delta' | 'deltax') {
    return createDeltaSession({
      runId: 'run',
      workerId: 'worker',
      identity: { taskId: 'task', novelId: 'novel', chapterId: 'chapter' },
      source: ['第一段', '第二段'],
      glossaryId: 'g',
      glossary: {},
      model: {
        protocol: 'openai-completions',
        endpoint: 'https://test.example/v1',
        model: 'model',
      },
      deltaMode,
      batchMaxParagraphs: 1,
    });
  }

  async function requestMessages(deltaMode: 'delta' | 'deltax') {
    const session = contextSession(deltaMode);
    const requests: PiAiSessionRequest[] = [];
    let index = 0;
    await runDeltaChapter(session, {
      key: 'secret',
      store: store(),
      transport: async (request) => {
        requests.push(request);
        const batch = session.batches[index++];
        return {
          message: assistant('submit_batch', {
            batchId: batch.id,
            entries: batch.paragraphIds.map((paragraphId) => ({
              paragraphId,
              translation: `译文-${paragraphId}`,
            })),
          }),
          usagePresence: presence,
        };
      },
    });
    return requests;
  }

  it('sends one fresh context message per batch', async () => {
    const requests = await requestMessages('deltax');
    expect(requests).toHaveLength(2);
    for (const request of requests) {
      expect(request.messages).toHaveLength(1);
    }
  });

  it('keeps native history for the default Delta mode', async () => {
    const requests = await requestMessages('delta');
    expect(requests.length).toBeGreaterThan(1);
    expect(requests[1].messages.length).toBeGreaterThan(1);
  });
});
