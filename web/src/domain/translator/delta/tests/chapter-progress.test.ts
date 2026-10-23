import { expect, it } from 'vitest';
import { createDeltaSession } from '@auto-novel/translator';
import { syncDeltaChapterProgress } from '../chapter-progress';

it('restores a claimed chapter omitted by normal translated filtering, including Review progress', () => {
  const session = createDeltaSession({
    runId: 'r',
    workerId: 'w',
    identity: { taskId: 't', novelId: 'n', chapterId: 'c', title: '第三章' },
    source: ['原文'],
    glossaryId: 'g',
    glossary: {},
    model: {
      protocol: 'openai-completions',
      endpoint: 'https://test.invalid',
      model: 'm',
    },
  });
  session.phase = 'reviewing';
  session.cursor = 1;
  const chapters = syncDeltaChapterProgress([], session);
  expect(chapters).toEqual([
    {
      chapterId: 'c',
      title: '第三章',
      order: 0,
      status: 'translating',
      segmentProgress: { completed: 1, total: 1 },
    },
  ]);
  session.phase = 'completed';
  expect(syncDeltaChapterProgress(chapters, session)).toEqual([
    { ...chapters[0], status: 'done' },
  ]);
  expect(chapters[0].status).toBe('translating');
});

it('restores progress directly from a lightweight summary', () => {
  const summary = {
    identity: { taskId: 't', novelId: 'n', chapterId: 'summary' },
    metadata: { chapterTitle: 'Summary chapter' },
    phase: 'completed' as const,
    cursor: 1,
    batchCount: 3,
  };
  expect(syncDeltaChapterProgress([], summary)).toEqual([
    {
      chapterId: 'summary',
      title: 'Summary chapter',
      order: 0,
      status: 'done',
      segmentProgress: { completed: 3, total: 3 },
    },
  ]);
});
