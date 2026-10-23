import { expect, it } from 'vitest';
import { createDeltaSession } from '@auto-novel/translator';
import { syncDeltaChapterProgress } from '../chapter-progress';
import type { ChapterMeta } from '../../TaskState';

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

it('keeps a readded task pending when an older or undated completed result exists', () => {
  const chapters: ChapterMeta[] = [
    { chapterId: 'c', title: 'C', order: 0, status: 'pending' },
  ];
  for (const taskCreatedAt of [1, undefined]) {
    const summary = {
      identity: { taskId: 't', novelId: 'n', chapterId: 'c', taskCreatedAt },
      metadata: {},
      phase: 'completed' as const,
      cursor: 2,
      batchCount: 2,
    };
    expect(syncDeltaChapterProgress(chapters, summary, 2)).toEqual(chapters);
    expect(syncDeltaChapterProgress([], summary, 2)[0]).toMatchObject({
      chapterId: 'c',
      status: 'done',
      segmentProgress: { completed: 2, total: 2 },
    });
  }
});

it('does not project older or undated unfinished drafts into a new task', () => {
  for (const taskCreatedAt of [1, undefined]) {
    const summary = {
      identity: { taskId: 't', novelId: 'n', chapterId: 'c', taskCreatedAt },
      metadata: {},
      phase: 'translating' as const,
      cursor: 1,
      batchCount: 2,
    };
    expect(syncDeltaChapterProgress([], summary, 2)).toEqual([]);
  }
});
