import { afterEach, expect, it, vi } from 'vitest';
import { onUnmounted, ref, shallowRef, toRaw } from 'vue';
import { TaskState } from '@/domain/translator/TaskState';
import { TranslateTaskDescriptor } from '@/model/Translator';
import { createDeltaSession } from '@auto-novel/translator';
import {
  summarizeDeltaSession,
  type DeltaSessionSummary,
} from '@/domain/translator/delta/session-summary';
import { syncDeltaChapterProgress } from '@/domain/translator/delta/chapter-progress';
import { groupDeltaSessions } from '@/domain/translator/delta/session-events';
import { setupSfc } from './sfc';

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

function fixture() {
  const job = {
    task: 'local/test',
    createAt: 1,
    excludedChapters: [] as string[],
  };
  const workspace = { ref: ref({ jobs: [job], workers: [] }) };
  let authorized = false;
  const task = {
    initialized: false,
    chapters: [{ chapterId: 'a', title: 'A', order: 0, status: 'pending' }],
    initMeta: vi.fn(async () => {
      if (!authorized) throw new Error('请先输入镜像认证');
      task.initialized = true;
    }),
  };
  class EmptyDeltaController {
    load = async () => {};
    taskHasDrafts = () => false;
    stopAll = async () => {};
  }
  class UnusedDependency {}
  class PipelineStub {
    queue = { clear: () => {} };
    clearLoops() {}
    registerTranslator() {}
    unregisterTranslator() {}
  }
  const setup = setupSfc<{
    selectedMode: string;
    initializationErrors: Map<string, string>;
    deltaSessions: DeltaSessionSummary[];
    visibleDeltaSessions: DeltaSessionSummary[];
    syncDeltaSessionsToTasks: (sessions: DeltaSessionSummary[]) => void;
    taskStates: Map<string, TaskState>;
    retryTaskCards: () => Promise<void> | void;
  }>(
    '../GptPipelineWorkspace.vue',
    { translatorId: 'gpt' },
    {
      '@novelia/web-kit': {
        Toast: { error: vi.fn(), info: vi.fn(), warning: vi.fn() },
      },
      '@/config': { forumPostUrls: {}, siteUrls: {} },
      '@/util': {
        useSimplifiedChineseConverter: async () => (text: string) => text,
      },
      '@vicons/material': {},
      'vue-draggable-plus': {},
      '@auto-novel/translator': {
        Semaphore: UnusedDependency,
        OpenAiTranslator: UnusedDependency,
        SakuraTranslator: UnusedDependency,
        TranslationPipeline: PipelineStub,
      },
      '@/stores': { useWorkspaceStore: () => workspace },
      '@/domain/translator/TaskExecutor': {},
      '@/domain/translator/IndexedDbSegmentCache': {
        IndexedDbSegmentCache: UnusedDependency,
      },
      '@/domain/translator/TranslationTask/createTranslationTask': {
        createTranslationTask: () => task,
      },
      '@/domain/translator/TaskState': { TaskState },
      '@/model/Translator': { TranslateTaskDescriptor },
      './components/PipelineTaskCard.vue': {},
      './components/DeltaCheckpointManager.vue': {},
      '@/domain/translator/delta/DeltaController': {
        DeltaController: EmptyDeltaController,
      },
      '@/domain/translator/delta/IndexedDbDeltaSessionStore': {
        IndexedDbDeltaSessionStore: UnusedDependency,
      },
      '@/domain/translator/delta/DeltaTaskSource': {
        createDeltaTaskSource: vi.fn(),
      },
      '@/domain/translator/delta/chapter-progress': {
        syncDeltaChapterProgress,
      },
      '@/domain/translator/delta/session-events': {
        applyDeltaSessionEvents: vi.fn((sessions) => sessions),
        groupDeltaSessions,
      },
    },
    { onUnmounted, shallowRef, toRaw },
  );
  cleanups.push(setup.unmount);
  return {
    setup,
    task,
    job,
    authorize: () => {
      authorized = true;
    },
  };
}

it('retries failed task initialization from the Delta toolbar without an existing checkpoint', async () => {
  const { setup, task, job, authorize } = fixture();

  await vi.waitFor(() => {
    expect(task.initMeta).toHaveBeenCalledTimes(1);
    expect(setup.state.initializationErrors.get(job.task)).toBe(
      '请先输入镜像认证',
    );
  });
  expect(setup.state.deltaSessions).toEqual([]);

  authorize();
  setup.state.selectedMode = 'delta';
  await setup.state.retryTaskCards();

  await vi.waitFor(() => {
    expect(task.initMeta).toHaveBeenCalledTimes(2);
    expect(setup.state.initializationErrors.has(job.task)).toBe(false);
    expect(setup.state.taskStates.get(job.task)?.initialized).toBe(true);
  });
});

it('keeps completed previews visible without an enabled worker and preserves new pending work', () => {
  const { setup, job } = fixture();
  const state = new TaskState(job.task);
  state.initChapters([
    { chapterId: 'a', title: 'A', order: 0, status: 'pending' },
  ]);
  setup.state.taskStates.set(job.task, state);
  job.excludedChapters = ['excluded'];
  const sessions = ['a', 'b', 'excluded', 'draft'].map((chapterId) => {
    const session = createDeltaSession({
      runId: 'old',
      workerId: 'removed-worker',
      identity: {
        taskId: job.task,
        taskCreatedAt: 0,
        novelId: 'novel',
        chapterId,
      },
      source: ['原文'],
      glossary: {},
      glossaryId: 'g',
      model: {
        protocol: 'openai-completions',
        endpoint: 'https://test.invalid',
        model: 'model',
      },
    });
    session.phase = chapterId === 'draft' ? 'translating' : 'completed';
    return summarizeDeltaSession(session);
  });
  setup.state.deltaSessions = sessions;
  expect(
    setup.state.visibleDeltaSessions.map(
      (session) => session.identity.chapterId,
    ),
  ).toEqual(['a', 'b', 'excluded']);
  setup.state.syncDeltaSessionsToTasks(sessions);
  expect(
    state.chapters.map((chapter) => [chapter.chapterId, chapter.status]),
  ).toEqual([
    ['a', 'pending'],
    ['b', 'done'],
  ]);
});
