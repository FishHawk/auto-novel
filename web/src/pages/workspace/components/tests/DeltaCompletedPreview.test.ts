import { afterEach, expect, it } from 'vitest';
import {
  createDeltaSession,
  isBlockingDeltaReviewIssue,
} from '@auto-novel/translator';
import { compactCompletedSession } from '../../../../domain/translator/delta/thinking-retention';
import { setupSfc } from './sfc';

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

it('preserves batch previews, usage and review differences after compacting completed history', () => {
  const session = createDeltaSession({
    runId: 'run',
    workerId: 'worker',
    identity: { taskId: 'task', novelId: 'novel', chapterId: 'chapter' },
    source: ['原文一', '原文二'],
    glossary: {},
    glossaryId: 'g',
    model: {
      protocol: 'openai-completions',
      endpoint: 'https://test.invalid',
      model: 'model',
    },
    batchMaxParagraphs: 1,
  });
  session.phase = 'completed';
  session.cursor = 2;
  session.translations = { p000001: '译文一', p000002: '译文二' };
  session.review = {
    id: 'review',
    draftHash: 'draft',
    completed: true,
    initialMessageCount: 0,
    totalAttempts: 2,
    attempts: 0,
    failures: 0,
    window: 1,
    systemPrompt: '审校提示',
    tools: [],
    messages: [{ role: 'user', content: '审校请求', timestamp: Date.now() }],
    requests: [
      {
        requestId: 'review-1',
        batchId: 'review',
        window: 1,
        attempt: 1,
        status: 'accepted',
        usage: {
          input: 30,
          output: 0,
          cacheRead: 0,
          cacheWrite: 0,
          totalTokens: 30,
          cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 },
        },
        usagePresence: {
          input: true,
          output: false,
          cacheRead: false,
          cacheWrite: false,
        },
      },
      {
        requestId: 'review-2',
        batchId: 'review',
        window: 1,
        attempt: 2,
        status: 'accepted',
      },
    ],
    options: { enabled: true, mode: 'new' },
    contextReads: 2,
    unresolved: [],
    originalTranslations: { p000001: '初译一', p000002: '译文二' },
    changes: [
      {
        paragraphId: 'p000001',
        translation: '译文一',
        category: 'accuracy',
        reason: '修正语义',
      },
    ],
  };
  session.requests = session.batches.map((batch, index) => ({
    requestId: `r${index}`,
    batchId: batch.id,
    window: 1,
    attempt: 1,
    status: 'accepted',
    usage: {
      input: 10,
      output: 5,
      cacheRead: 2,
      cacheWrite: 0,
      totalTokens: 17,
      cost: { input: 0, output: 0, cacheRead: 0, cacheWrite: 0, total: 0 },
    },
    usagePresence: {
      input: true,
      output: true,
      cacheRead: true,
      cacheWrite: false,
    },
  }));
  compactCompletedSession(session);
  const setup = setupSfc<{
    displayParagraphs: { id: string; jp: string; zh: string }[];
    currentThinking: string;
    reviewThinking: string;
    activeMetrics: { input: number; output: number; cacheRead: number };
    reviewMetrics: { value: string }[];
    computedReviewChanges: {
      originalText: string;
      translation: string;
      reason: string;
    }[];
    reviewToolHistoryMessage: string;
    selectedBatchIndex: number;
  }>(
    'DeltaPreviewModal.vue',
    { show: true, title: 'Chapter', session },
    {
      '@auto-novel/translator': { isBlockingDeltaReviewIssue },
      '@novelia/web-kit': { XDialog: {} },
    },
    { useThemeVars: () => ({}) },
  );
  cleanups.push(setup.unmount);
  const { state } = setup;
  expect(
    state.displayParagraphs.map((paragraph) => [paragraph.jp, paragraph.zh]),
  ).toEqual([
    ['原文一', '译文一'],
    ['原文二', '译文二'],
  ]);
  expect(state.currentThinking).toBe('');
  expect(state.reviewThinking).toBe('');
  expect(state.activeMetrics).toMatchObject({
    input: 20,
    output: 10,
    cacheRead: 4,
  });
  expect(state.reviewMetrics[0].value).toBe('30（覆盖 1/2 请求）');
  expect(state.computedReviewChanges[0]).toMatchObject({
    originalText: '初译一',
    translation: '译文一',
    reason: '修正语义',
  });
  expect(state.reviewToolHistoryMessage).toContain('已清理');
  expect(state.reviewToolHistoryMessage).toContain('2');
  state.selectedBatchIndex = 1;
  expect(state.displayParagraphs.map((paragraph) => paragraph.zh)).toEqual([
    '译文二',
  ]);
  expect(state.activeMetrics).toMatchObject({
    input: 10,
    output: 5,
    cacheRead: 2,
  });
});
