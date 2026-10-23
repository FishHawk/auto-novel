import { afterEach, expect, it, vi } from 'vitest';
import { onUnmounted, ref, shallowRef, toRaw } from 'vue';
import { TaskState } from '@/domain/translator/TaskState';
import { TranslateTaskDescriptor } from '@/model/Translator';
import { setupSfc } from './sfc';

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));

it('retries failed task initialization from the Delta toolbar without an existing checkpoint', async () => {
  const job = { task: 'local/test', createAt: 1 };
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
    deltaSessions: unknown[];
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
        syncDeltaChapterProgress: vi.fn((chapters) => chapters),
      },
      '@/domain/translator/delta/session-events': {
        applyDeltaSessionEvent: vi.fn((sessions) => sessions),
        groupDeltaSessions: vi.fn(() => new Map()),
      },
    },
    { onUnmounted, shallowRef, toRaw },
  );
  cleanups.push(setup.unmount);

  await vi.waitFor(() => {
    expect(task.initMeta).toHaveBeenCalledTimes(1);
    expect(setup.state.initializationErrors.get(job.task)).toBe(
      '请先输入镜像认证',
    );
  });
  expect(setup.state.deltaSessions).toEqual([]);

  authorized = true;
  setup.state.selectedMode = 'delta';
  await setup.state.retryTaskCards();

  await vi.waitFor(() => {
    expect(task.initMeta).toHaveBeenCalledTimes(2);
    expect(setup.state.initializationErrors.has(job.task)).toBe(false);
    expect(setup.state.taskStates.get(job.task)?.initialized).toBe(true);
  });
});
