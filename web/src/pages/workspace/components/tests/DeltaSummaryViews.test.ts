import { afterEach, describe, expect, it, vi } from 'vitest';
import { nextTick } from 'vue';
import type { DeltaChapterSession } from '@auto-novel/translator';
import type { DeltaSessionSummary } from '../../../../domain/translator/delta/session-summary';
import { syncDeltaChapterProgress } from '../../../../domain/translator/delta/chapter-progress';
import { useDeltaSessionDetail } from '../../../../domain/translator/delta/useDeltaSessionDetail';
import { setupSfc } from './sfc';

const cleanups: (() => void)[] = [];
afterEach(() => cleanups.splice(0).forEach((cleanup) => cleanup()));
function summary() {
  return {
    id: 'checkpoint',
    runId: 'run',
    revision: 2,
    workerId: 'worker',
    identity: { taskId: 'task', novelId: 'novel', chapterId: 'chapter' },
    metadata: { title: 'Novel', chapterTitle: 'Chapter' },
    phase: 'translating',
    cursor: 1,
    batchCount: 2,
    paragraphCount: 4,
    completedParagraphs: 2,
    totalAttempts: 3,
    requestCount: 3,
    tokenTotals: { cacheRead: 8, output: 9 },
    usage: {
      input: { total: 20, reported: 1 },
      output: { total: 0, reported: 0 },
      cacheRead: { total: 8, reported: 2 },
      cacheWrite: { total: 0, reported: 0 },
    },
  } as DeltaSessionSummary;
}
async function flush() {
  await Promise.resolve();
  await nextTick();
}
const composableImports = {
  '@/domain/translator/delta/chapter-progress': { syncDeltaChapterProgress },
  '@/domain/translator/delta/useDeltaSessionDetail': { useDeltaSessionDetail },
};
type TaskStateView = {
  openPreview: (id: string) => Promise<void>;
  showDeltaPreview: boolean;
  showPipelinePreview: boolean;
  activeDeltaSession: DeltaChapterSession | null;
  deltaDetailError: string | null;
  retryDeltaDetail: () => void;
  deltaTotalCacheRead: number;
  deltaTotalOutput: number;
};
function taskFixture(
  load: (id: string) => Promise<DeltaChapterSession | undefined>,
  initialized = true,
) {
  const setup = setupSfc<TaskStateView>(
    'PipelineTaskCard.vue',
    {
      job: { task: 'task' },
      translatorId: 'gpt',
      deltaSessions: [summary()],
      loadDeltaSession: load,
      taskState: {
        initialized,
        chapters: [{ chapterId: 'chapter', title: 'Chapter' }],
        chapterStates: new Map(),
      },
    },
    {
      ...composableImports,
      '@novelia/web-kit': { Toast: { error: vi.fn() }, XDialog: {} },
      '@vicons/material': {},
      '@/domain/translator/TaskState': {},
      '@/model/Translator': {},
      '@/domain/translator/TranslationTask/createTranslationTask': {
        createTranslationTask: () => {
          throw new Error('Legacy detail must not load');
        },
      },
      './ChapterPreviewModal.vue': {},
      './DeltaPreviewModal.vue': {},
    },
    { useThemeVars: () => ({}) },
  );
  cleanups.push(setup.unmount);
  return setup;
}

it('opens Delta details lazily while preserving summary token totals', async () => {
  const load = vi.fn(
    async () =>
      ({ id: 'checkpoint', runId: 'run', revision: 2 }) as DeltaChapterSession,
  );
  const setup = taskFixture(load);
  expect(setup.state.deltaTotalCacheRead).toBe(8);
  expect(setup.state.deltaTotalOutput).toBe(9);
  expect(load).not.toHaveBeenCalled();
  await setup.state.openPreview('chapter');
  await flush();
  expect(load).toHaveBeenCalledOnce();
  expect(setup.state.activeDeltaSession?.id).toBe('checkpoint');
  expect(setup.state.showPipelinePreview).toBe(false);
  setup.state.showDeltaPreview = false;
  await flush();
  expect(setup.state.activeDeltaSession).toBeNull();
});

it('keeps a failed Delta preview retryable without opening the legacy preview', async () => {
  const load = vi
    .fn()
    .mockRejectedValueOnce(new Error('Checkpoint read failed'))
    .mockResolvedValueOnce({ id: 'checkpoint', runId: 'run', revision: 2 });
  const setup = taskFixture(load);
  await setup.state.openPreview('chapter');
  await flush();
  expect(setup.state.deltaDetailError).toBe('Checkpoint read failed');
  expect(setup.state.showDeltaPreview).toBe(true);
  expect(setup.state.showPipelinePreview).toBe(false);
  setup.state.retryDeltaDetail();
  await flush();
  expect(setup.state.activeDeltaSession?.id).toBe('checkpoint');
});

it('opens an available Delta checkpoint without initializing legacy task metadata', async () => {
  const load = vi.fn(
    async () =>
      ({ id: 'checkpoint', runId: 'run', revision: 2 }) as DeltaChapterSession,
  );
  const setup = taskFixture(load, false);
  await setup.state.openPreview('chapter');
  await flush();
  expect(load).toHaveBeenCalledOnce();
  expect(setup.state.showDeltaPreview).toBe(true);
});
