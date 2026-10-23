<script lang="ts" setup>
import { Toast } from '@novelia/web-kit';
import {
  BookOutlined,
  DeleteOutlineOutlined,
  PlayArrowOutlined,
  PlusOutlined,
  RefreshOutlined,
  StopOutlined,
} from '@vicons/material';
import { VueDraggable } from 'vue-draggable-plus';
import {
  allowModels,
  Semaphore,
  OpenAiTranslator,
  SakuraTranslator,
  TranslationPipeline,
} from '@auto-novel/translator';
import type {
  DeltaChapterSession,
  TranslatorTracker,
} from '@auto-novel/translator';

import { forumPostUrls, siteUrls } from '@/config';
import { useWorkspaceStore } from '@/stores';
import { useSimplifiedChineseConverter } from '@/util';
import { TaskExecutor } from '@/domain/translator/TaskExecutor';
import { IndexedDbSegmentCache } from '@/domain/translator/IndexedDbSegmentCache';
import { createTranslationTask } from '@/domain/translator/TranslationTask/createTranslationTask';
import type {
  TranslationTask,
  TranslatorId,
} from '@/domain/translator/TranslationTask/types';
import { TaskState } from '@/domain/translator/TaskState';
import type { ChapterStatus } from '@/domain/translator/TaskState';
import type {
  GptPipelineWorker,
  SakuraWorker,
  TranslateJob,
  TranslateJobRecord,
} from '@/model/Translator';
import { TranslateTaskDescriptor } from '@/model/Translator';
import PipelineTaskCard from './components/PipelineTaskCard.vue';
import DeltaCheckpointManager from './components/DeltaCheckpointManager.vue';
import { DeltaController } from '@/domain/translator/delta/DeltaController';
import { IndexedDbDeltaSessionStore } from '@/domain/translator/delta/IndexedDbDeltaSessionStore';
import { createDeltaTaskSource } from '@/domain/translator/delta/DeltaTaskSource';
import { syncDeltaChapterProgress } from '@/domain/translator/delta/chapter-progress';
import type {
  DeltaSessionSummary,
  DeltaSessionEvent,
} from '@/domain/translator/delta/session-summary';
import {
  applyDeltaSessionEvents,
  groupDeltaSessions,
} from '@/domain/translator/delta/session-events';

const props = defineProps<{ translatorId: TranslatorId }>();
const isSakura = computed(() => props.translatorId === 'sakura');
const segCacheName = isSakura.value ? 'sakura-seg-cache' : 'gpt-seg-cache';

const workerStore = useWorkspaceStore(
  isSakura.value ? 'sakura' : 'gpt-pipeline',
);
const jobStore = useWorkspaceStore(props.translatorId);

const jobs = computed(() => jobStore.ref.value.jobs);

const showWorkerModal = ref(false);
const showLocalVolumeDrawer = ref(false);

/** 同时处理的章节数上限 */
const GLOBAL_WINDOW = 66;
/** 同时 fetch 的章节数上限 */
const FETCH_CONCURRENCY = 1;
const fetchSemaphore = new Semaphore(FETCH_CONCURRENCY);
/** pipeline 内同时翻译的分块的高水位标记 */
const HIGH_WATER_MARK = 100;
const pipeline = new TranslationPipeline(
  HIGH_WATER_MARK,
  undefined,
  new IndexedDbSegmentCache(segCacheName),
  isSakura.value ? 'chapter' : 'segment',
);

// ========== 任务状态管理 ==========

const taskStates = ref(new Map<string, TaskState>());
const taskCache = new Map<string, TranslationTask>();
const taskVersions = ref(new Map<string, number>());
const initializationErrors = ref(new Map<string, string>());
/** 同一版本只初始化一次；跨标签页替换对象不能重建执行状态。 */
const initializingTasks = new Map<string, { createAt: number }>();

interface WorkerState {
  running: boolean;
  concurrency: { current: number; max: number };
  allowUpload?: boolean;
}
const workerStates = ref(new Map<string, WorkerState>());
const workerErrors = computed(() => {
  const errors = new Map<string, number>();
  for (const [, state] of taskStates.value) {
    for (const [, chapterState] of state.chapterStates) {
      for (const seg of chapterState.segments) {
        if (seg.status === 'error' && seg.translatorId) {
          errors.set(seg.translatorId, (errors.get(seg.translatorId) ?? 0) + 1);
        }
      }
    }
  }
  return errors;
});
const executingTasks = reactive(new Set<string>());

// ========== Delta / DeltaX / ReviewX ==========

const selectedMode = ref<'pipeline' | 'delta'>('pipeline');
const maintainingCheckpoints = ref(false);
let closing = false;

const deltaSessions = shallowRef<DeltaSessionSummary[]>([]);
const deltaWorkerStates = ref(new Map<string, WorkerState>());
const deltaRuns = reactive(new Map<string, Promise<void>>());
const deltaChapterActions = reactive(new Map<string, string>());
const deltaEnabledWorkerIds = computed(
  () =>
    new Set(
      workerStore.ref.value.workers
        .filter((worker) => (worker as GptPipelineWorker).deltaEnabled === true)
        .map((worker) => worker.id),
    ),
);
const hasEnabledDeltaWorkers = computed(
  () => deltaEnabledWorkerIds.value.size > 0,
);
const visibleDeltaSessions = computed(() =>
  deltaSessions.value.filter(
    (session) =>
      session.phase === 'completed' ||
      deltaEnabledWorkerIds.value.has(session.workerId),
  ),
);
const deltaSessionsByTask = computed(
  (previous?: Map<string, DeltaSessionSummary[]>) =>
    groupDeltaSessions(visibleDeltaSessions.value, previous),
);

function syncDeltaSessionsToTasks(sessions: DeltaSessionSummary[]) {
  for (const session of sessions) {
    if (
      session.phase !== 'completed' &&
      !deltaEnabledWorkerIds.value.has(session.workerId)
    )
      continue;
    const taskId = session.identity.taskId;
    const state = taskStates.value.get(taskId);
    const job = jobs.value.find((item) => item.task === taskId);
    if (
      !state ||
      !job ||
      job.excludedChapters?.includes(session.identity.chapterId)
    )
      continue;
    state.chapters = syncDeltaChapterProgress(
      state.chapters,
      session,
      job.createAt,
    );
    (job as TranslateJobRecord).progress = {
      finished: state.chapters.filter((chapter) => chapter.status === 'done')
        .length,
      error: state.chapters.filter((chapter) => chapter.status === 'error')
        .length,
      total: state.chapters.length,
    };
  }
}

/**
 * Checkpoints save several times per request. Coalescing notifications keeps
 * the session list and every task card from being rewritten on each commit.
 */
const pendingDeltaEvents: DeltaSessionEvent[] = [];
let deltaFlushTimer: ReturnType<typeof setTimeout> | undefined;

function flushDeltaEvents() {
  if (deltaFlushTimer !== undefined) {
    clearTimeout(deltaFlushTimer);
    deltaFlushTimer = undefined;
  }
  if (!pendingDeltaEvents.length) return;
  const events = pendingDeltaEvents.splice(0);
  const before = new Map(
    deltaSessions.value.map((session) => [session.id, session]),
  );
  let reset: DeltaSessionSummary[] | undefined;
  const changed = new Map<string, DeltaSessionSummary>();
  for (const event of events) {
    if (event.type === 'reset') {
      reset = event.sessions;
      continue;
    }
    if (event.type !== 'upsert') continue;
    const previous = before.get(event.session.id);
    if (
      event.session.phase !== 'completed' &&
      previous?.runId !== event.session.runId
    ) {
      const job = jobs.value.find(
        (item) => item.task === event.session.identity.taskId,
      );
      if (job) delete job.finishAt;
    }
    changed.set(event.session.id, event.session);
  }
  deltaSessions.value = applyDeltaSessionEvents(deltaSessions.value, events);
  if (reset) syncDeltaSessionsToTasks(deltaSessions.value);
  else if (changed.size) syncDeltaSessionsToTasks([...changed.values()]);
}

function queueDeltaEvent(event: DeltaSessionEvent) {
  pendingDeltaEvents.push(event);
  deltaFlushTimer ??= setTimeout(flushDeltaEvents, 80);
}

const delta = isSakura.value
  ? undefined
  : new DeltaController({
      store: new IndexedDbDeltaSessionStore(),
      source: createDeltaTaskSource,
      jobs: () => jobs.value,
      window: GLOBAL_WINDOW,
      simplifyText: async (text) =>
        (await useSimplifiedChineseConverter())(text),
      onSessionEvent: queueDeltaEvent,
      onWorkerChange: (id, running, current, max) => {
        deltaWorkerStates.value.set(id, {
          running,
          concurrency: { current, max },
        });
      },
      onChapterChange: (taskId, chapterId, status, session) => {
        const state = taskStates.value.get(taskId);
        if (state) {
          const chapter = state.chapters.find(
            (item) => item.chapterId === chapterId,
          );
          if (chapter) {
            chapter.status = session?.pauseReason
              ? session.pauseReason.includes('停止')
                ? 'pending'
                : 'paused'
              : status;
            if (session) {
              chapter.segmentProgress = {
                completed:
                  session.phase === 'completed'
                    ? session.batches.length
                    : session.cursor,
                total: session.batches.length,
              };
            }
          }
          const job = jobs.value.find((item) => item.task === taskId);
          if (job) {
            (job as TranslateJobRecord).progress = {
              finished: state.chapters.filter((item) => item.status === 'done')
                .length,
              error: state.chapters.filter((item) => item.status === 'error')
                .length,
              total: state.chapters.length,
            };
          }
        }
      },
      onTaskDone: (taskId, total) => {
        const job = jobs.value.find((item) => item.task === taskId);
        if (job) {
          job.finishAt = Date.now();
          (job as TranslateJobRecord).progress = {
            finished: total,
            error: 0,
            total,
          };
          releaseFinishedTaskRuntime(job.task);
        }
      },
    });

const loadDeltaSession = (id: string) =>
  delta ? delta.getSession(id) : Promise.resolve(undefined);
const deltaReady = delta?.load() ?? Promise.resolve();
void deltaReady.catch((error) => Toast.error(`Delta 检查点读取失败：${error}`));

const activeDeltaWorkers = computed(() =>
  [...deltaWorkerStates.value]
    .filter(([, state]) => state.running)
    .map(([id]) => id),
);
const deltaBusySessionIds = computed(
  () =>
    new Set(
      deltaSessions.value
        .filter(
          (session) =>
            deltaChapterActions.has(session.id) ||
            deltaRuns.has(session.workerId),
        )
        .map((session) => session.id),
    ),
);
const deltaBusy = computed(
  () =>
    activeDeltaWorkers.value.length > 0 ||
    deltaRuns.size > 0 ||
    maintainingCheckpoints.value,
);

function workerRunning(workerId: string) {
  return (
    !!workerStates.value.get(workerId)?.running ||
    !!deltaWorkerStates.value.get(workerId)?.running
  );
}
function workerConcurrency(worker: GptPipelineWorker | SakuraWorker) {
  return (
    (selectedMode.value === 'delta'
      ? deltaWorkerStates.value.get(worker.id)
      : workerStates.value.get(worker.id)
    )?.concurrency ?? {
      current: 0,
      max: worker.concurrency ?? 1,
    }
  );
}
function workerDeltaPausedCount(workerId: string) {
  if (!deltaEnabledWorkerIds.value.has(workerId)) return 0;
  return deltaSessions.value.filter(
    (session) =>
      session.workerId === workerId &&
      session.pauseReason &&
      !session.pauseReason.includes('停止'),
  ).length;
}
function canResumeDelta(workerId: string) {
  if (!deltaEnabledWorkerIds.value.has(workerId)) return false;
  return deltaSessions.value.some(
    (session) => session.workerId === workerId && session.phase !== 'completed',
  );
}

interface MetadataState {
  task: TranslationTask;
  createAt: number;
  controller: AbortController;
  settled: boolean;
}
/** 元数据独立于章节窗口；已结束的尝试保留到任务清除，避免重复上传。 */
const metadataTasks = new Map<string, MetadataState>();
const preparationTasks = new Map<
  string,
  { controller: AbortController; settled: boolean }
>();

let processLoopAbortController: AbortController | null = null;
let activeProcessors = 0;

onUnmounted(() => {
  closing = true;
  initializingTasks.clear();
  abortMetadataTasks();
  abortPreparationTasks();
  pipeline.clearLoops();
  pipeline.queue.clear();
  processLoopAbortController?.abort();
  processLoopAbortController = null;
  executingTasks.clear();
  workerStates.value.clear();
  if (deltaFlushTimer !== undefined) {
    clearTimeout(deltaFlushTimer);
    deltaFlushTimer = undefined;
  }
  pendingDeltaEvents.length = 0;
  void delta?.stopAll();
  deltaRuns.clear();
  deltaWorkerStates.value.clear();
});
// ========== 任务过滤 ==========

type TaskFilter = 'all' | 'done' | 'pending';
const taskFilter = ref<TaskFilter>('all');

const filterOptions: { label: string; value: TaskFilter }[] = [
  { label: '全部', value: 'all' },
  { label: '已完成', value: 'done' },
  { label: '未完成', value: 'pending' },
];

function getTaskStatus(taskDesc: string): TaskFilter {
  const job = jobs.value.find((j) => j.task === taskDesc);
  return job?.finishAt ? 'done' : 'pending';
}

const filteredJobs = computed(() => {
  if (taskFilter.value === 'all') return jobs.value;
  return jobs.value.filter((j) => getTaskStatus(j.task) === taskFilter.value);
});

async function getOrCreateTask(taskDesc: string): Promise<TranslationTask> {
  let task = taskCache.get(taskDesc);
  if (!task) {
    const { desc, params } = TranslateTaskDescriptor.parse(taskDesc);
    task = createTranslationTask(desc, props.translatorId, params);
    taskCache.set(taskDesc, task);
  }
  return task;
}

function getNextJob(
  jobs: TranslateJob[],
  taskStatesMap: Map<string, TaskState>,
): { job: TranslateJob; chapterId: string; state: TaskState } | null {
  for (const job of jobs) {
    if (job.finishAt) continue;
    if (taskCache.get(job.task)?.needsPreparation) continue;
    const state = taskStatesMap.get(job.task);
    if (!state) continue;
    for (const ch of state.chapters) {
      if (ch.status !== 'pending') continue;
      ch.status = 'translating';
      return { job, chapterId: ch.chapterId, state };
    }
  }
  return null;
}

function countPendingChapters(
  jobs: TranslateJob[],
  taskStatesMap: Map<string, TaskState>,
): number {
  let count = 0;
  for (const job of jobs) {
    if (job.finishAt) continue;
    if (taskCache.get(job.task)?.needsPreparation) continue;
    const state = taskStatesMap.get(job.task);
    if (!state) continue;
    for (const ch of state.chapters) {
      if (ch.status === 'pending') count++;
    }
  }
  return count;
}

function hasRunningWorker(): boolean {
  return [...workerStates.value.values()].some(
    (s) => s.running && s.allowUpload !== undefined,
  );
}

function canUpload(): boolean {
  return (
    hasRunningWorker() &&
    [...workerStates.value.values()].every(
      (s) => !s.running || s.allowUpload !== false,
    )
  );
}

function abortMetadataTasks(): void {
  for (const [key, state] of metadataTasks) {
    if (state.settled) continue;
    state.controller.abort();
    metadataTasks.delete(key);
  }
}

function clearTaskRuntimeState(task: string): void {
  initializingTasks.delete(task);
  preparationTasks.get(task)?.controller.abort();
  preparationTasks.delete(task);
  metadataTasks.get(task)?.controller.abort();
  metadataTasks.delete(task);
  taskStates.value.delete(task);
  taskCache.delete(task);
  taskVersions.value.delete(task);
  initializationErrors.value.delete(task);
}

// ========== 全局处理循环 ==========

/**
 * 其他标签页修改工作区时，localStorage 同步会整体替换 jobs 里的对象，
 * 执行中持有的 job 引用随之失效。写回状态前必须重新取当前对象。
 */
function findCurrentJob(job: TranslateJob): TranslateJobRecord | undefined {
  return jobs.value.find(
    (it) => it.task === job.task && it.createAt === job.createAt,
  );
}

/** Finished jobs keep only display metadata; chapter text is re-read on preview. */
function releaseFinishedTaskRuntime(task: string): void {
  taskStates.value.get(task)?.releaseAllChapterTexts();
  taskCache.delete(task);
  const metadata = metadataTasks.get(task);
  if (metadata) {
    metadata.controller.abort();
    metadataTasks.delete(task);
  }
  const preparation = preparationTasks.get(task);
  if (preparation) {
    preparation.controller.abort();
    preparationTasks.delete(task);
  }
}

function updateJobCompletion(
  job: TranslateJob,
  task: TranslationTask,
  state: TaskState,
): void {
  const currentJob = findCurrentJob(job);
  if (
    !currentJob ||
    task.needsPreparation ||
    taskCache.get(job.task) !== task ||
    taskStates.value.get(job.task) !== state
  ) {
    return;
  }
  const metadata = metadataTasks.get(job.task);
  if (
    state.chapters.every((chapter) => chapter.status === 'done') &&
    (!isSakura.value || !task.needsMetadata || metadata?.settled)
  ) {
    currentJob.finishAt = Date.now();
    releaseFinishedTaskRuntime(job.task);
  }
}

function abortPreparationTasks(): void {
  for (const [key, preparation] of preparationTasks) {
    preparation.controller.abort();
    // 等待插件返回后再释放占位
    if (preparation.settled) preparationTasks.delete(key);
  }
}

function prepareTaskOnce(
  job: TranslateJob,
  task: TranslationTask,
  state: TaskState,
): void {
  if (
    !task.prepareExecution ||
    !canUpload() ||
    preparationTasks.has(job.task)
  ) {
    return;
  }
  const preparation = { controller: new AbortController(), settled: false };
  preparationTasks.set(job.task, preparation);
  const signal = preparation.controller.signal;
  const canPrepare = () =>
    !signal.aborted &&
    preparationTasks.get(job.task) === preparation &&
    taskCache.get(job.task) === task &&
    taskStates.value.get(job.task) === state &&
    !!findCurrentJob(job) &&
    canUpload();
  task
    .prepareExecution({
      signal,
      canUpload: canPrepare,
      onLog: (message) => console.log(`[${job.task}] ${message}`),
    })
    .then(() => {
      if (!canPrepare()) return;
      state.initChapters(task.chapters);
      const currentJob = findCurrentJob(job)!;
      currentJob.progress = {
        finished: task.chapters.filter((chapter) => chapter.status === 'done')
          .length,
        error: 0,
        total: task.chapters.length,
      };
      updateJobCompletion(job, task, state);
    })
    .catch((error) => {
      if (!signal.aborted) console.warn(`[${job.task}] 任务准备失败`, error);
    })
    .finally(() => {
      if (preparationTasks.get(job.task) !== preparation) return;
      preparation.settled = true;
      if (signal.aborted || !task.needsPreparation) {
        preparationTasks.delete(job.task);
        runProcessLoop();
      }
    });
}

/**
 * 翻译标题、简介和目录。与旧版工作区一致，GPT 不翻译元数据。
 * 不阻塞章节翻译，失败只记录日志。
 */
function translateMetadataOnce(
  job: TranslateJob,
  task: TranslationTask,
  state: TaskState,
): void {
  if (!isSakura.value || !task.needsMetadata || !task.translateMetadata) return;
  if (!canUpload() || metadataTasks.has(job.task)) return;
  const metadata: MetadataState = {
    task,
    createAt: job.createAt,
    controller: new AbortController(),
    settled: false,
  };
  metadataTasks.set(job.task, metadata);
  const signal = metadata.controller.signal;
  const canUploadMetadata = () =>
    !signal.aborted &&
    metadataTasks.get(job.task) === metadata &&
    taskCache.get(job.task) === metadata.task &&
    jobs.value.some(
      (it) => it.task === job.task && it.createAt === metadata.createAt,
    ) &&
    canUpload();

  // 简介可能含换行，按行翻译后再按原行数拼回
  const translateTexts = async (texts: string[]) => {
    signal.throwIfAborted();
    const lineCounts = texts.map((text) => text.split('\n').length);
    // 分段失败时 pipeline 会回退为原文，不能把原文当译文上传
    let failed = false;
    const lines = (
      await pipeline.translate(
        texts.join('\n'),
        task.glossary,
        undefined,
        signal,
        { onSegError: () => (failed = true) },
      )
    ).split('\n');
    signal.throwIfAborted();
    if (failed) throw new Error('分段翻译失败');
    if (
      lines.length !== lineCounts.reduce((total, count) => total + count, 0)
    ) {
      throw new Error('元数据译文行数不匹配');
    }
    return lineCounts.map((count) => lines.splice(0, count).join('\n'));
  };
  task
    .translateMetadata(translateTexts, signal, canUploadMetadata)
    .then(() => {
      if (!signal.aborted && metadataTasks.get(job.task) === metadata) {
        console.log(`[${job.task}] 元数据翻译完成`);
      }
    })
    .catch((e) => {
      if (!signal.aborted) console.warn(`[${job.task}] 元数据翻译失败`, e);
    })
    .finally(() => {
      if (metadataTasks.get(job.task) !== metadata) return;
      metadata.settled = true;
      updateJobCompletion(job, task, state);
    });
}

function runProcessLoop(): void {
  if (selectedMode.value === 'delta' || deltaBusy.value) return;
  if (!hasRunningWorker()) return;

  for (const job of jobs.value) {
    if (job.finishAt) continue;
    const task = taskCache.get(job.task);
    const state = taskStates.value.get(job.task);
    if (task?.initialized && state?.initialized) {
      if (task.needsPreparation) {
        prepareTaskOnce(job, task, state);
        continue;
      }
      translateMetadataOnce(job, task, state);
    }
  }

  const pendingCount = countPendingChapters(jobs.value, taskStates.value);
  if (pendingCount === 0) return;
  processLoopAbortController ??= new AbortController();
  const controller = processLoopAbortController;
  const signal = controller.signal;

  const processOne = async () => {
    while (!signal.aborted && hasRunningWorker()) {
      const item = getNextJob(jobs.value, taskStates.value);
      if (!item) break;

      const { job, chapterId, state } = item;
      executingTasks.add(job.task);

      const task = await getOrCreateTask(job.task);
      const executor = new TaskExecutor(
        task,
        pipeline,
        fetchSemaphore,
        () =>
          !signal.aborted &&
          taskCache.get(job.task) === task &&
          taskStates.value.get(job.task) === state &&
          !!findCurrentJob(job) &&
          canUpload(),
      );

      const segmentTracker = state.getChapterState(chapterId);
      if (!segmentTracker) continue;
      await executor
        .executeChapter(
          chapterId,
          {
            onChapterStatus: (cid: string, st: ChapterStatus) => {
              state.updateStatus(cid, st);
              if (st === 'done' || st === 'error')
                state.releaseFinishedChapterTexts();
            },
            onProgress: (finished: number, error: number, total: number) => {
              const currentJob = findCurrentJob(job);
              if (currentJob) {
                currentJob.progress = { finished, error, total };
              }
            },
            onLog: (msg: string) => console.log(`[${job.task}] ${msg}`),
            segmentTracker,
          },
          signal,
        )
        .then(() => {
          if (taskStates.value.get(job.task) !== state) return;
          const allProcessed = state.chapters.every(
            (c) => c.status === 'done' || c.status === 'error',
          );
          if (allProcessed) {
            executingTasks.delete(job.task);
          }
          updateJobCompletion(job, task, state);
        });
    }
  };

  // 运行中新增的待翻章节（新任务、重试、任务延迟初始化）也要补满窗口，
  // 否则章节并发会一直被卡在循环启动时的待翻章节数。
  const spawnCount = Math.min(GLOBAL_WINDOW - activeProcessors, pendingCount);
  for (let i = 0; i < spawnCount; i++) {
    activeProcessors++;
    processOne().finally(() => {
      activeProcessors--;
      if (activeProcessors === 0) {
        executingTasks.clear();
        if (processLoopAbortController === controller) {
          processLoopAbortController = null;
        }
      }
      if (countPendingChapters(jobs.value, taskStates.value) > 0) {
        runProcessLoop();
      }
    });
  }
}

// ========== Worker 生命周期管理 ==========

const startWorker = async (workerId: string) => {
  if (workerStates.value.get(workerId)?.running) return;
  if (deltaBusy.value) {
    Toast.warning('请先停止 Delta 翻译器，再使用默认启动');
    return;
  }
  if (
    !isSakura.value &&
    jobs.value.some((job) => delta?.taskHasDrafts(job.task))
  ) {
    Toast.warning('请先通过“数据管理”清除未完成的 Delta 数据，再使用默认启动');
    return;
  }
  selectedMode.value = 'pipeline';

  const w = workerStore.ref.value.workers.find((w) => w.id === workerId);
  if (!w) {
    Toast.error(`翻译器 ${workerId} 不存在`);
    return;
  }

  const state = reactive<WorkerState>({
    running: true,
    concurrency: { current: 0, max: w.concurrency ?? 1 },
  });
  workerStates.value.set(workerId, state);

  try {
    const log = (msg: string) => console.log(`[${w.id}] ${msg}`);
    const t = isSakura.value
      ? await SakuraTranslator.create({ ...(w as SakuraWorker), log })
      : new OpenAiTranslator({ ...(w as GptPipelineWorker), log });

    if (workerStates.value.get(workerId) !== state) return;

    state.allowUpload = !(t instanceof SakuraTranslator) || t.allowUpload();

    const workerTracker: TranslatorTracker = {
      onConcurrencyChange: (current: number, max: number) => {
        state.concurrency = { current, max };
      },
    };
    pipeline.registerTranslator(t, w.concurrency, workerTracker, w.id);

    runProcessLoop();
  } catch (e) {
    if (workerStates.value.get(workerId) !== state) return;
    stopWorker(workerId);
    Toast.error(`翻译器启动失败：${e}`);
  }
};

const stopWorker = async (workerId: string) => {
  const deltaRunning =
    !!delta &&
    (delta.isRunning(workerId) ||
      deltaRuns.has(workerId) ||
      [...deltaChapterActions.values()].includes(workerId));
  if (deltaRunning) {
    const stopping = delta!.stopWorker(workerId);
    for (const [sessionId, owner] of deltaChapterActions) {
      if (owner === workerId) deltaChapterActions.delete(sessionId);
    }
    deltaRuns.delete(workerId);
    await stopping;
  }
  const state = workerStates.value.get(workerId);
  if (!state?.running) return;

  pipeline.unregisterTranslator(workerId);

  workerStates.value.set(workerId, { ...state, running: false });

  if ([...workerStates.value.values()].every((s) => !s.running)) {
    abortMetadataTasks();
    abortPreparationTasks();
    processLoopAbortController?.abort();
    processLoopAbortController = null;
    pipeline.queue.clear();
  }
};

const startAllWorkers = () => {
  for (const w of workerStore.ref.value.workers) startWorker(w.id);
};

const startAllDeltaWorkers = () => {
  for (const w of workerStore.ref.value.workers) {
    if ((w as GptPipelineWorker).deltaEnabled !== true) continue;
    void startDeltaWorker(w.id);
  }
};

const stopDeltaAll = async () => {
  if (!delta) return;
  deltaChapterActions.clear();
  deltaRuns.clear();
  await delta.stopAll();
};

const stopAllWorkers = async () => {
  for (const w of workerStore.ref.value.workers) await stopWorker(w.id);
  await stopDeltaAll();
};

const deleteJob = async (task: string) => {
  if (executingTasks.has(task)) {
    Toast.error('任务正在翻译中，请先停止');
    return;
  }
  if (delta?.hasTask(task)) {
    Toast.error('任务正在 Delta 翻译中，请先停止对应翻译器');
    return;
  }
  if (delta) {
    try {
      await delta.discardTask(task);
    } catch (error) {
      Toast.error(`清理 Delta 数据失败：${error}`);
      return;
    }
  }
  jobStore.deleteJob(task);
  clearTaskRuntimeState(task);
};

const deleteAllJobs = async () => {
  for (const job of [...filteredJobs.value]) await deleteJob(job.task);
};

const removeChapter = async (task: string, chapterId: string) => {
  const job = jobs.value.find((item) => item.task === task);
  if (!job) return;
  if (executingTasks.has(task) || delta?.hasTask(task)) {
    Toast.error('任务正在翻译中，请先停止后再移除章节');
    return;
  }
  if (!job.excludedChapters?.includes(chapterId)) {
    job.excludedChapters = [...(job.excludedChapters ?? []), chapterId];
  }
  const state = taskStates.value.get(task);
  if (state) {
    state.chapters = state.chapters.filter(
      (chapter) => chapter.chapterId !== chapterId,
    );
    state.chapterStates.delete(chapterId);
    const done = state.chapters.filter(
      (chapter) => chapter.status === 'done',
    ).length;
    (job as TranslateJobRecord).progress = {
      finished: done,
      error: state.chapters.filter((chapter) => chapter.status === 'error')
        .length,
      total: state.chapters.length,
    };
    if (!state.chapters.length || done === state.chapters.length)
      job.finishAt = Date.now();
    else delete job.finishAt;
  }
  if (delta) {
    try {
      await delta.discardChapterByTask(task, chapterId);
    } catch (error) {
      Toast.warning(`章节已移出队列，但 Delta 数据清理失败：${error}`);
    }
  }
};

const taskCardRefs = ref<InstanceType<typeof PipelineTaskCard>[]>([]);
async function retryTaskCards() {
  let hasFailed = false;
  for (const job of [...filteredJobs.value]) {
    if (!initializationErrors.value.has(job.task)) continue;
    hasFailed = true;
    await retryTask(job.task);
  }
  for (const card of taskCardRefs.value) {
    if (card?.hasFailedChapters?.()) {
      card.retryAllFailed();
      hasFailed = true;
    }
  }
  if (!hasFailed) {
    Toast.info('没有需要重试的失败任务');
  }
}

async function retryDeltaWorkers() {
  flushDeltaEvents();
  const workers = [
    ...new Set(
      deltaSessions.value
        .filter(
          (session) =>
            session.pauseReason &&
            deltaEnabledWorkerIds.value.has(session.workerId),
        )
        .map((session) => session.workerId),
    ),
  ];
  if (!workers.length) Toast.info('没有需要重试的 Delta 失败章节');
  await Promise.all(
    workers.map((workerId) => startDeltaWorker(workerId, true)),
  );
}

async function retryTask(task: string) {
  flushDeltaEvents();
  const job = jobs.value.find((item) => item.task === task);
  if (!job) return;
  if (
    initializationErrors.value.has(task) ||
    !taskStates.value.get(task)?.initialized
  ) {
    delete job.finishAt;
    await initializeJob(job);
    if (!taskStates.value.get(task)?.initialized) return;
  }
  const paused = deltaSessions.value.filter(
    (session) =>
      session.identity.taskId === task &&
      session.pauseReason &&
      deltaEnabledWorkerIds.value.has(session.workerId),
  );
  if (paused.length) {
    for (const workerId of new Set(paused.map((session) => session.workerId)))
      await startDeltaWorker(workerId, true);
    return;
  }
  const workers = [
    ...new Set(
      deltaSessions.value
        .filter(
          (session) =>
            session.identity.taskId === task &&
            deltaEnabledWorkerIds.value.has(session.workerId),
        )
        .map((session) => session.workerId),
    ),
  ];
  if (selectedMode.value === 'delta' && workers.length) {
    for (const workerId of workers) await startDeltaWorker(workerId);
    return;
  }
  return runProcessLoop();
}

async function actOnDeltaChapter(
  sessionId: string,
  kind: 'skip' | 'restart' | 'retry',
) {
  flushDeltaEvents();
  const session = deltaSessions.value.find((item) => item.id === sessionId);
  if (!session) return;
  await startDeltaWorker(session.workerId, false, { kind, sessionId });
}

async function startDeltaWorker(
  workerId: string,
  retry = false,
  action?: { kind: 'skip' | 'restart' | 'retry'; sessionId: string },
) {
  flushDeltaEvents();
  if (!delta || closing || maintainingCheckpoints.value) return;
  if (hasRunningWorker()) {
    Toast.warning('请先停止默认翻译器，再启动 Delta');
    return;
  }
  const runKey = action?.sessionId ?? workerId;
  if (deltaRuns.has(runKey)) return;
  if (action && deltaChapterActions.has(action.sessionId)) return;
  if (!action && delta.isRunning(workerId)) return;
  const worker = workerStore.ref.value.workers.find(
    (item) => item.id === workerId,
  ) as GptPipelineWorker | undefined;
  if (!worker) {
    Toast.error('找不到绑定的翻译器，请先恢复翻译器配置');
    return;
  }
  if (worker.deltaEnabled !== true) {
    Toast.warning('该翻译器未启用 Delta 翻译');
    return;
  }
  selectedMode.value = 'delta';
  // 基础 Delta 不对外开放；旧配置统一按 DeltaX 启动。
  const effectiveWorker: GptPipelineWorker = {
    ...toRaw(worker),
    deltaMode: worker.deltaMode === 'deltaxs' ? 'deltaxs' : 'deltax',
  };
  const run = action
    ? action.kind === 'skip'
      ? delta.skipReview(action.sessionId, effectiveWorker)
      : action.kind === 'retry'
        ? delta.retryReview(action.sessionId, effectiveWorker)
        : delta.restartChapter(action.sessionId, effectiveWorker)
    : retry
      ? delta.retryWorker(effectiveWorker)
      : delta.startWorker(effectiveWorker);
  deltaRuns.set(runKey, run);
  if (action) deltaChapterActions.set(action.sessionId, workerId);
  void run
    .catch((error) => Toast.error(`Delta 运行失败：${error}`))
    .finally(() => {
      if (deltaRuns.get(runKey) === run) deltaRuns.delete(runKey);
      if (action) deltaChapterActions.delete(action.sessionId);
    });
}

async function discardDeltaScope(
  scope: { sessionId: string } | { novelId: string } | { pending: true },
) {
  if (!delta || deltaBusy.value || hasRunningWorker()) return;
  maintainingCheckpoints.value = true;
  try {
    await Toast.promise(
      (async () => {
        await deltaReady;
        await delta.load();
        flushDeltaEvents();
        const before = [...deltaSessions.value];
        if ('sessionId' in scope) await delta.discardChapter(scope.sessionId);
        else if ('novelId' in scope) await delta.discardNovel(scope.novelId);
        else await delta.discardPending();
        await delta.load();
        flushDeltaEvents();
        const remaining = new Set(deltaSessions.value.map((item) => item.id));
        for (const session of before) {
          if (remaining.has(session.id)) continue;
          const chapter = taskStates.value
            .get(session.identity.taskId)
            ?.chapters.find(
              (item) => item.chapterId === session.identity.chapterId,
            );
          if (!chapter) continue;
          chapter.segmentProgress = undefined;
          if (chapter.status === 'translating' || chapter.status === 'paused')
            chapter.status = 'pending';
        }
      })(),
      { success: '清除数据成功', error: '清除数据失败' },
    );
  } finally {
    maintainingCheckpoints.value = false;
  }
}

const clearCache = () =>
  Toast.promise(new IndexedDbSegmentCache(segCacheName).clear(), {
    success: '缓存清除成功',
    error: '缓存清除失败',
  });

async function initializeJob(job: TranslateJob) {
  initializationErrors.value.delete(job.task);
  try {
    const task = await getOrCreateTask(job.task);
    if (!task.initialized) await task.initMeta();
    const currentJob = findCurrentJob(job);
    if (!currentJob || taskCache.get(job.task) !== task) return;
    const excluded = new Set(job.excludedChapters ?? []);
    const chapters = task.chapters.filter(
      (chapter) => !excluded.has(chapter.chapterId),
    );
    const state = reactive(new TaskState(job.task)) as TaskState;
    state.initChapters(chapters);
    taskStates.value.set(job.task, state);
    taskVersions.value.set(job.task, job.createAt);
    const doneCount = chapters.filter(
      (chapter) => chapter.status === 'done',
    ).length;
    currentJob.progress = {
      finished: doneCount,
      error: 0,
      total: chapters.length,
    };
    if (delta) {
      flushDeltaEvents();
      syncDeltaSessionsToTasks(
        deltaSessions.value.filter(
          (session) => session.identity.taskId === job.task,
        ),
      );
    }
    updateJobCompletion(job, task, state);
  } catch (error) {
    if (
      !jobs.value.some(
        (item) => item.task === job.task && item.createAt === job.createAt,
      )
    )
      return;
    initializationErrors.value.set(
      job.task,
      error instanceof Error ? error.message : String(error),
    );
  }
}

watch(
  () => [...jobs.value],
  async (workspaceJobs) => {
    const uninitialized: {
      job: TranslateJob;
      initialization: { createAt: number };
    }[] = [];
    const activeTasks = new Set(workspaceJobs.map((job) => job.task));
    const knownTasks = new Set([
      ...taskStates.value.keys(),
      ...taskCache.keys(),
      ...taskVersions.value.keys(),
      ...initializingTasks.keys(),
      ...metadataTasks.keys(),
      ...preparationTasks.keys(),
      ...initializationErrors.value.keys(),
    ]);
    for (const task of knownTasks) {
      if (!activeTasks.has(task)) clearTaskRuntimeState(task);
    }

    for (const job of workspaceJobs) {
      if (taskVersions.value.get(job.task) !== job.createAt) {
        clearTaskRuntimeState(job.task);
      }
      const state = taskStates.value.get(job.task);
      if (
        taskVersions.value.get(job.task) === job.createAt &&
        (state?.initialized || initializingTasks.has(job.task))
      ) {
        continue;
      }
      if (job.finishAt) {
        // A finished task no longer needs translated chapter text or the full
        // TOC/glossary task; previews re-read them on demand.
        taskStates.value.get(job.task)?.releaseAllChapterTexts();
        taskCache.delete(job.task);
        metadataTasks.get(job.task)?.controller.abort();
        metadataTasks.delete(job.task);
        preparationTasks.get(job.task)?.controller.abort();
        preparationTasks.delete(job.task);
        taskStates.value.set(
          job.task,
          reactive(new TaskState(job.task)) as TaskState,
        );
        taskVersions.value.set(job.task, job.createAt);
        continue;
      }
      const initialization = { createAt: job.createAt };
      taskVersions.value.set(job.task, job.createAt);
      initializingTasks.set(job.task, initialization);
      uninitialized.push({ job, initialization });
    }
    await Promise.all(
      uninitialized.map(async ({ job, initialization }) => {
        try {
          await initializeJob(job);
        } finally {
          if (initializingTasks.get(job.task) === initialization) {
            initializingTasks.delete(job.task);
          }
        }
      }),
    );
    if (uninitialized.length > 0) runProcessLoop();
  },
  { immediate: true },
);
</script>

<template>
  <div class="layout-content">
    <n-h1>{{ isSakura ? 'Sakura' : 'GPT' }}工作区</n-h1>
    <bulletin>
      <n-p>
        支持并发和查看章节分块状态的新工作区。目前处于测试阶段，有问题请在群内反馈。
      </n-p>
      <template v-if="isSakura">
        <n-flex>
          <n-a
            :href="forumPostUrls.sakuraDeployGuide"
            target="_blank"
            rel="noopener noreferrer"
          >
            本地部署教程
          </n-a>
          /
          <span>
            <n-a
              :href="forumPostUrls.sakuraAutoDlGuide"
              target="_blank"
              rel="noopener noreferrer"
            >
              AutoDL教程
            </n-a>
            :
            <n-a
              href="https://www.autodl.com/console/instance/list"
              target="_blank"
              rel="noopener noreferrer"
            >
              控制台
            </n-a>
          </span>
          /
          <n-a
            :href="siteUrls.sakuraMonitor"
            target="_blank"
            rel="noopener noreferrer"
          >
            共享 Sakura 当前负载
          </n-a>
        </n-flex>
        <n-p>允许上传的模型如下，禁止一切试图突破上传检查的操作。</n-p>
        <n-ul>
          <n-li v-for="({ repo }, model) in allowModels" :key="model">
            [
            <n-a
              :href="`https://huggingface.co/${repo}/blob/main/${model}.gguf`"
              target="_blank"
              rel="noopener noreferrer"
            >
              HF
            </n-a>
            /
            <n-a
              :href="`https://hf-mirror.com/${repo}/blob/main/${model}.gguf`"
              target="_blank"
              rel="noopener noreferrer"
            >
              国内镜像
            </n-a>
            ]
            {{ model }}
          </n-li>
        </n-ul>
      </template>
      <template v-else>
        <n-flex>
          <n-a
            :href="forumPostUrls.usageGuide"
            target="_blank"
            rel="noopener noreferrer"
          >
            使用教程
          </n-a>
          /
          <n-a
            href="https://chat.deepseek.com"
            target="_blank"
            rel="noopener noreferrer"
          >
            DeepSeek Chat
          </n-a>
          /
          <n-a
            href="https://platform.deepseek.com/usage"
            target="_blank"
            rel="noopener noreferrer"
          >
            DeepSeek API
          </n-a>
        </n-flex>
        <n-p>不再支持GPT web，推荐使用deepseek API，价格很低。</n-p>
        <n-p>本地小说支持韩语等其他语种，网络小说/文库小说暂时只允许日语。</n-p>
      </template>
    </bulletin>

    <section-header title="翻译器">
      <c-button
        label="添加翻译器"
        :icon="PlusOutlined"
        @action="showWorkerModal = true"
      />
    </section-header>

    <n-flex vertical>
      <n-flex
        align="center"
        justify="space-between"
        :wrap="true"
        style="gap: 12px"
      >
        <c-action-wrapper title="默认" align="center">
          <n-button-group size="small">
            <c-button
              label="启动全部"
              :icon="PlayArrowOutlined"
              :round="false"
              :disabled="deltaBusy"
              @action="startAllWorkers"
            />
            <c-button
              label="停止全部"
              :icon="StopOutlined"
              :round="false"
              @action="stopAllWorkers"
            />
            <c-button-confirm
              hint="真的要清空分块缓存吗？"
              label="清空缓存"
              :icon="DeleteOutlineOutlined"
              :round="false"
              @action="clearCache"
            />
          </n-button-group>
        </c-action-wrapper>
        <c-action-wrapper v-if="!isSakura" title="Delta" align="center">
          <n-button-group size="small">
            <c-button
              label="启动全部"
              :icon="PlayArrowOutlined"
              :round="false"
              :disabled="
                maintainingCheckpoints ||
                hasRunningWorker() ||
                !hasEnabledDeltaWorkers
              "
              @action="startAllDeltaWorkers"
            />
            <c-button
              label="停止全部"
              :icon="StopOutlined"
              :round="false"
              @action="stopDeltaAll"
            />
            <delta-checkpoint-manager
              :sessions="deltaSessions"
              :disabled="deltaBusy || hasRunningWorker()"
              @discard-chapter="discardDeltaScope({ sessionId: $event })"
              @discard-novel="discardDeltaScope({ novelId: $event })"
              @discard-pending="discardDeltaScope({ pending: true })"
            />
          </n-button-group>
        </c-action-wrapper>
      </n-flex>
    </n-flex>

    <n-empty
      v-if="workerStore.ref.value.workers.length === 0"
      description="没有翻译器"
    />
    <n-list v-else class="worker-list">
      <vue-draggable
        v-model="workerStore.ref.value.workers"
        :animation="150"
        handle=".drag-trigger"
        filter=".is-running"
      >
        <n-list-item
          v-for="w of workerStore.ref.value.workers"
          :key="w.id"
          :class="{ 'is-running': workerRunning(w.id) }"
        >
          <pipeline-worker-card
            :translator-id="translatorId"
            :worker="w"
            :running="workerRunning(w.id)"
            :concurrency="workerConcurrency(w)"
            :error-count="workerErrors.get(w.id) ?? 0"
            :paused-count="workerDeltaPausedCount(w.id)"
            :delta-resume="canResumeDelta(w.id)"
            :start-disabled="maintainingCheckpoints || deltaBusy"
            :delta-disabled="maintainingCheckpoints || hasRunningWorker()"
            @start="startWorker"
            @start-delta="startDeltaWorker"
            @stop="stopWorker"
            @delete="workerStore.deleteWorker"
          />
        </n-list-item>
      </vue-draggable>
    </n-list>

    <section-header title="任务队列">
      <c-button
        label="本地书架"
        :icon="BookOutlined"
        @action="showLocalVolumeDrawer = true"
      />
    </section-header>

    <n-flex vertical>
      <c-action-wrapper title="状态">
        <c-radio-group
          v-model:value="taskFilter"
          :options="filterOptions"
          size="small"
        />
      </c-action-wrapper>
      <c-action-wrapper title="操作" align="center">
        <n-button-group size="small">
          <c-button-confirm
            hint="真的要删除筛选出的任务吗？"
            label="删除筛选任务"
            :icon="DeleteOutlineOutlined"
            :round="false"
            @action="deleteAllJobs"
          />
          <c-button
            label="重试失败任务"
            :icon="RefreshOutlined"
            :round="false"
            @action="retryTaskCards"
          />
        </n-button-group>
      </c-action-wrapper>
    </n-flex>
    <n-divider style="margin: 16px 0 8px" />
    <n-empty v-if="filteredJobs.length === 0" description="没有任务" />
    <div v-else class="task-list">
      <pipeline-task-card
        v-for="job of filteredJobs"
        ref="taskCardRefs"
        :key="job.task"
        :job="job"
        :translator-id="translatorId"
        :task-state="taskStates.get(job.task)"
        :task-cache-entry="taskCache.get(job.task)"
        :initialization-error="initializationErrors.get(job.task)"
        :delta-sessions="deltaSessionsByTask.get(job.task) ?? []"
        :load-delta-session="loadDeltaSession"
        :delta-busy-session-ids="deltaBusySessionIds"
        :delta-actions-disabled="maintainingCheckpoints || hasRunningWorker()"
        :removable="
          !maintainingCheckpoints && !deltaBusy && !hasRunningWorker()
        "
        @skip-review="actOnDeltaChapter($event, 'skip')"
        @retry-review="actOnDeltaChapter($event, 'retry')"
        @restart-delta="actOnDeltaChapter($event, 'restart')"
        @retry-delta="retryDeltaWorkers"
        @remove-chapter="removeChapter"
        @delete="deleteJob"
        @retry="retryTask"
        @top="
          (task: string) =>
            jobStore.topJob(
              jobs.find((j: { task: string }) => j.task === task)!,
            )
        "
        @bottom="
          (task: string) =>
            jobStore.bottomJob(
              jobs.find((j: { task: string }) => j.task === task)!,
            )
        "
      />
    </div>
  </div>

  <local-volume-list-specific-translation
    v-model:show="showLocalVolumeDrawer"
    :type="translatorId"
  />
  <sakura-worker-modal v-if="isSakura" v-model:show="showWorkerModal" />
  <gpt-pipeline-worker-modal v-else v-model:show="showWorkerModal" />
</template>

<style scoped>
.task-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
</style>
