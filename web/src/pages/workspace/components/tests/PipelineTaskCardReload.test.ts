import { afterEach, expect, it, vi } from 'vitest';
import { reactive, nextTick } from 'vue';
import {
  TaskState,
  ChapterSegmentState,
  type ChapterMeta,
} from '../../../../domain/translator/TaskState';
import type { DeltaChapterSession } from '@auto-novel/translator';
import type { DeltaSessionSummary } from '../../../../domain/translator/delta/session-summary';
import { syncDeltaChapterProgress } from '../../../../domain/translator/delta/chapter-progress';
import { useDeltaSessionDetail } from '../../../../domain/translator/delta/useDeltaSessionDetail';
import { setupSfc } from './sfc';

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));
type CardState = {
  taskStatus: string;
  progressLabel: string;
  chapterMetas: ChapterMeta[];
  effectiveTaskState: TaskState;
  expanded: boolean;
  toggleExpand: () => Promise<void>;
  openPreview: (id: string) => Promise<void>;
};
function fixture(
  state?: TaskState,
  chapters: ChapterMeta[] = [],
  taskCreatedAt?: number,
  options: {
    job?: Record<string, unknown>;
    initializationError?: string;
    deltaPhase?: DeltaSessionSummary['phase'];
    deltaPauseReason?: string;
  } = {},
) {
  const load = vi.fn(
    async () =>
      ({ id: 'checkpoint', runId: 'run', revision: 1 }) as DeltaChapterSession,
  );
  const snapshot = {
    id: 'checkpoint',
    runId: 'run',
    revision: 1,
    identity: {
      taskId: 'task',
      novelId: 'novel',
      chapterId: 'completed',
      taskCreatedAt,
    },
    metadata: { chapterTitle: 'Stored completed chapter' },
    phase: options.deltaPhase ?? 'completed',
    pauseReason: options.deltaPauseReason,
    cursor: 2,
    batchCount: 2,
  } as DeltaSessionSummary;
  const task = {
    initialized: false,
    chapters,
    initMeta: vi.fn(async () => {
      task.initialized = true;
    }),
  };
  const setup = setupSfc<CardState>(
    'PipelineTaskCard.vue',
    {
      job: { task: 'task', createAt: 10, finishAt: 1, ...options.job },
      initializationError: options.initializationError,
      translatorId: 'gpt',
      taskState: state,
      taskCacheEntry: task,
      deltaSessions: [snapshot],
      loadDeltaSession: load,
    },
    {
      '@/domain/translator/delta/useDeltaSessionDetail': {
        useDeltaSessionDetail,
      },
      '@/domain/translator/delta/chapter-progress': {
        syncDeltaChapterProgress,
      },
      '@novelia/web-kit': { Toast: { error: vi.fn() }, XDialog: {} },
      '@vicons/material': {},
      '@/domain/translator/TaskState': { TaskState, ChapterSegmentState },
      '@/model/Translator': {},
      '@/domain/translator/TranslationTask/createTranslationTask': {},
      './ChapterPreviewModal.vue': {},
      './DeltaPreviewModal.vue': {},
    },
    { useThemeVars: () => ({}) },
  );
  cleanups.push(setup.unmount);
  return { ...setup, load, task };
}

it('never calls an unread task completed just because chapter metadata is empty', () => {
  const setup = fixture(undefined, [], undefined, {
    job: { finishAt: undefined },
  });
  expect(setup.state.taskStatus).toBe('loading');
  expect(setup.state.progressLabel).toBe('正在读取章节');
});

it('shows metadata failures as errors instead of completed zero counts', () => {
  const setup = fixture(undefined, [], undefined, {
    job: { finishAt: undefined },
    initializationError: '请先输入镜像认证',
  });
  expect(setup.state.taskStatus).toBe('error');
  expect(setup.state.progressLabel).toBe('章节读取失败');
});

it('keeps an unfinished Delta checkpoint visible even when initial selection had no pending work', () => {
  const state = reactive(new TaskState('task')) as TaskState;
  state.initChapters([
    { chapterId: 'completed', title: 'A', order: 0, status: 'paused' },
  ]);
  const setup = fixture(state, state.chapters, undefined, {
    deltaPhase: 'readyToSubmit',
    deltaPauseReason: '暂存后检查点未完成',
    job: { finishAt: undefined },
  });
  expect(setup.state.taskStatus).toBe('pending');
});

it('restores completed summary chapters when reload metadata filters out every finished chapter', async () => {
  const taskState = reactive(new TaskState('task')) as TaskState;
  const setup = fixture(taskState);
  await setup.state.toggleExpand();
  expect(setup.state.expanded).toBe(true);
  expect(setup.state.chapterMetas).toEqual([
    {
      chapterId: 'completed',
      title: 'Stored completed chapter',
      order: 0,
      status: 'done',
      segmentProgress: { completed: 2, total: 2 },
    },
  ]);
  expect(taskState.initialized).toBe(true);
  expect(setup.load).not.toHaveBeenCalled();
  await setup.state.openPreview('completed');
  await nextTick();
  expect(setup.load).toHaveBeenCalledOnce();
});

it('preserves unrelated TOC rows and their status while merging historical checkpoints', async () => {
  const taskState = reactive(new TaskState('task')) as TaskState;
  const existing: ChapterMeta = {
    chapterId: 'other',
    title: 'Other',
    order: 9,
    status: 'error',
  };
  taskState.initChapters([existing]);
  const setup = fixture(taskState);
  await setup.state.toggleExpand();
  expect(setup.state.chapterMetas[0]).toEqual(existing);
  expect(setup.state.chapterMetas.map((chapter) => chapter.chapterId)).toEqual([
    'other',
    'completed',
  ]);
  expect(setup.task.initMeta).not.toHaveBeenCalled();
  expect(setup.load).not.toHaveBeenCalled();
});

it('retains a local task state for cards without an externally provided state', async () => {
  const setup = fixture();
  await setup.state.toggleExpand();
  expect(setup.state.chapterMetas.map((chapter) => chapter.chapterId)).toEqual([
    'completed',
  ]);
  expect(setup.state.effectiveTaskState.chapters).toEqual(
    setup.state.chapterMetas,
  );
  const state = setup.state.effectiveTaskState;
  await setup.state.toggleExpand();
  await setup.state.toggleExpand();
  expect(setup.state.effectiveTaskState).toBe(state);
  expect(setup.task.initMeta).toHaveBeenCalledOnce();
  expect(setup.load).not.toHaveBeenCalled();
});

it('restores older completed results for preview after readding a task without reading details', async () => {
  const taskState = reactive(new TaskState('task')) as TaskState;
  const setup = fixture(taskState, [], 9);
  await setup.state.toggleExpand();
  expect(setup.state.chapterMetas[0]).toMatchObject({
    chapterId: 'completed',
    status: 'done',
    segmentProgress: { completed: 2, total: 2 },
  });
  expect(setup.load).not.toHaveBeenCalled();
  await setup.state.openPreview('completed');
  await nextTick();
  expect(setup.load).toHaveBeenCalledOnce();
});

it('does not restore an older unfinished job generation into the current chapter grid', async () => {
  const taskState = reactive(new TaskState('task')) as TaskState;
  const setup = fixture(taskState, [], 9, { deltaPhase: 'translating' });
  await setup.state.toggleExpand();
  expect(setup.state.chapterMetas).toEqual([]);
  expect(setup.load).not.toHaveBeenCalled();
});

it('does not mark new pending work completed using an older or undated result', async () => {
  for (const taskCreatedAt of [9, undefined]) {
    const taskState = reactive(new TaskState('task')) as TaskState;
    taskState.initChapters([
      { chapterId: 'completed', title: 'C', order: 0, status: 'pending' },
    ]);
    const setup = fixture(taskState, taskState.chapters, taskCreatedAt, {
      job: { finishAt: undefined },
    });
    await setup.state.toggleExpand();
    expect(setup.state.chapterMetas[0].status).toBe('pending');
    expect(setup.state.taskStatus).toBe('pending');
  }
});

it('restores checkpoints belonging to the current job generation', async () => {
  const taskState = reactive(new TaskState('task')) as TaskState;
  const setup = fixture(taskState, [], 10);
  await setup.state.toggleExpand();
  expect(setup.state.chapterMetas.map((chapter) => chapter.chapterId)).toEqual([
    'completed',
  ]);
  expect(setup.load).not.toHaveBeenCalled();
});
