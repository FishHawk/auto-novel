import { cloneDeep } from 'lodash-es';
import { v4 as uuidv4 } from 'uuid';
import {
  buildOpenAiProfileParams,
  openAiProfiles,
  createDeltaSession,
  deltaChapterKey,
  deltaFingerprint,
  runDeltaChapter,
  runDeltaReview,
  reopenDeltaReviewRetryWindow,
  normalizeDeltaReviewOptions,
  skipDeltaReview,
  reopenDeltaRetryWindow,
  createPiAiSession,
  DELTA_PROMPT_VERSION,
  Semaphore,
  type DeltaChapterSession,
  type DeltaModelSnapshot,
  type DeltaChapterIdentity,
} from '@auto-novel/translator';
import type { GptPipelineWorker, TranslateJob } from '@/model/Translator';
import type { ChapterStatus } from '../TaskState';
import type { ChapterDetail } from '../TranslationTask/types';
import type { DeltaSessionRepository, DeltaTaskSource } from './types';
import {
  simplifyDeltaOutput,
  type DeltaTextConverter,
} from './simplify-output';
import {
  summarizeDeltaSession,
  type DeltaSessionSummary,
  type DeltaSessionEvent,
} from './session-summary';

interface ControllerOptions {
  store: DeltaSessionRepository;
  source: (taskDesc: string) => Promise<DeltaTaskSource>;
  jobs: () => TranslateJob[];
  /** Shared ceiling across every Delta worker in this workspace. */
  window?: number;
  onSessionEvent?: (event: DeltaSessionEvent) => void;
  onWorkerChange?: (
    id: string,
    running: boolean,
    current: number,
    max: number,
  ) => void;
  onChapterChange?: (
    taskId: string,
    chapterId: string,
    status: ChapterStatus,
    session?: DeltaChapterSession,
  ) => void;
  onTaskDone?: (taskId: string, total: number) => void;
  /** Best-effort normalization before the final fingerprint and upload. */
  simplifyText?: DeltaTextConverter;
  runChapter?: typeof runDeltaChapter;
  runReview?: typeof runDeltaReview;
}

interface WorkerRun {
  controller: AbortController;
  promise: Promise<void>;
  targetSessionId?: string;
  targetTaskId?: string;
  current: number;
  max: number;
  attempted: Set<string>;
  errors: unknown[];
}
interface ChapterRun extends WorkerRun {
  workerId: string;
}

type ChapterClaim =
  | { session: DeltaSessionSummary; taskId: string; chapterId: string }
  | {
      identity: DeltaChapterIdentity;
      taskId: string;
      chapterId: string;
      title: string;
    };
const doneKey = (job: TranslateJob, chapterKey: string) =>
  JSON.stringify([chapterKey, job.task, job.createAt]);

export function deltaWorkerModel(
  worker: GptPipelineWorker,
): DeltaModelSnapshot {
  const format =
    openAiProfiles.find((p) => p.id === worker.profile?.id)?.apiFormat ??
    'openai';
  return {
    protocol: format === 'openai' ? 'openai-completions' : format,
    endpoint: worker.endpoint,
    model: worker.model,
    profile: worker.profile?.id,
    requestParams: buildOpenAiProfileParams(
      worker.profile?.id,
      worker.profile?.values,
    ),
  };
}

function translationHash(
  detail: ChapterDetail,
  includeVersion = false,
): string {
  return deltaFingerprint({
    paragraphs: detail.oldParagraphZh ?? null,
    glossary: sortedGlossary(detail.oldGlossary ?? {}),
    ...(includeVersion && detail.translationVersion !== undefined
      ? { version: detail.translationVersion }
      : {}),
  });
}
function sortedGlossary(glossary: Record<string, string>) {
  return Object.fromEntries(
    Object.entries(glossary).sort(([a], [b]) => a.localeCompare(b)),
  );
}

/** Owns chapter slots; every claimed chapter remains bound through upload drain. */
export class DeltaController {
  private sessions = new Map<string, DeltaSessionSummary>();
  private workers = new Map<string, WorkerRun>();
  private retries = new Map<
    string,
    { controller: AbortController; promise: Promise<void> }
  >();
  private chapterRuns = new Map<string, ChapterRun>();
  private chapterSlots = new Map<string, Semaphore>();
  private claimed = new Set<string>();
  private claimedTasks = new Map<string, string>();
  private sources = new Map<string, Promise<DeltaTaskSource>>();
  private done = new Set<string>();
  private readonly window: number;
  private readonly globalSlots: Semaphore;

  constructor(private options: ControllerOptions) {
    this.window = Math.max(1, Math.floor(options.window ?? 66));
    this.globalSlots = new Semaphore(this.window);
  }

  async load(): Promise<void> {
    // Summaries are indexed separately; loading them no longer deserializes
    // every chapter's full transcript before the workspace can render.
    this.sessions = new Map(
      (await this.options.store.listSummaries()).map((s) => [s.id, s]),
    );
    this.options.onSessionEvent?.({
      type: 'reset',
      sessions: [...this.sessions.values()],
    });
    // Expired diagnostics are pruned after the list is available. The store
    // guards against repeating the scan on every reload.
    try {
      await this.options.store.pruneThinking?.();
    } catch (error) {
      console.warn('Delta 历史清理失败，将在下次打开时重试', error);
    }
  }
  getSession(id: string): Promise<DeltaChapterSession | undefined> {
    return this.options.store.get(id);
  }
  private async requireSession(id: string): Promise<DeltaChapterSession> {
    const session = await this.getSession(id);
    if (!session) throw new Error('章节断点已不存在，请刷新工作区');
    return session;
  }
  isRunning(id: string) {
    return (
      this.workers.has(id) ||
      this.retries.has(id) ||
      this.chapterActions(id).length > 0
    );
  }
  activeCount(id: string) {
    return (
      (this.workers.get(id)?.current ?? 0) +
      this.chapterActions(id).reduce((sum, run) => sum + run.current, 0)
    );
  }
  private chapterActions(workerId: string) {
    return [...this.chapterRuns.values()].filter(
      (run) => run.workerId === workerId,
    );
  }
  private chapterActionBlocked(workerId: string, sessionId: string) {
    return (
      this.workers.has(workerId) ||
      this.retries.has(workerId) ||
      this.claimed.has(sessionId) ||
      this.chapterRuns.has(sessionId) ||
      this.chapterActions(workerId).some((run) => run.controller.signal.aborted)
    );
  }
  hasTask(taskId: string) {
    return [...this.claimedTasks.values()].includes(taskId);
  }
  hasDrafts() {
    return [...this.sessions.values()].some((s) => s.phase !== 'completed');
  }
  taskHasDrafts(taskId: string) {
    return [...this.sessions.values()].some(
      (s) =>
        s.identity.taskId.split('?')[0] === taskId.split('?')[0] &&
        s.phase !== 'completed',
    );
  }

  startWorker(worker: GptPipelineWorker, taskId?: string): Promise<void> {
    const retry = this.retries.get(worker.id);
    if (retry) return retry.promise;
    const actions = this.chapterActions(worker.id);
    if (actions.length)
      return Promise.all(actions.map((run) => run.promise)).then(() => {});
    return this.beginWorker(worker, taskId);
  }
  private beginWorker(
    worker: GptPipelineWorker,
    taskId?: string,
  ): Promise<void> {
    const existing = this.workers.get(worker.id);
    if (existing) return existing.promise;
    const snapshot = cloneDeep(worker);
    const run: WorkerRun = {
      controller: new AbortController(),
      promise: Promise.resolve(),
      targetTaskId: taskId,
      current: 0,
      max: Math.min(snapshot.concurrency ?? 1, this.window),
      attempted: new Set(),
      errors: [],
    };
    this.workers.set(snapshot.id, run);
    this.updateWorker(snapshot.id, run);
    run.promise = Promise.allSettled(
      Array.from({ length: run.max }, () => this.slot(snapshot, run)),
    )
      .then(async (results) => {
        await this.completeTasks(taskId);
        const failed = results.find((r) => r.status === 'rejected');
        if (failed?.status === 'rejected') throw failed.reason;
        if (run.errors.length) throw run.errors[0];
      })
      .finally(() => {
        this.workers.delete(snapshot.id);
        this.updateWorker(snapshot.id, run);
        this.clearIdleSources();
      });
    return run.promise;
  }

  async stopWorker(id: string): Promise<void> {
    const retry = this.retries.get(id);
    retry?.controller.abort();
    const run = this.workers.get(id);
    run?.controller.abort();
    const actions = this.chapterActions(id);
    for (const action of actions) action.controller.abort();
    await Promise.allSettled([
      run?.promise,
      retry?.promise,
      ...actions.map((action) => action.promise),
    ]);
  }
  async stopAll(): Promise<void> {
    for (const retry of this.retries.values()) retry.controller.abort();
    for (const run of this.workers.values()) run.controller.abort();
    for (const run of this.chapterRuns.values()) run.controller.abort();
    await Promise.allSettled(
      [
        ...this.workers.values(),
        ...this.retries.values(),
        ...this.chapterRuns.values(),
      ].map((r) => r.promise),
    );
  }
  retryWorker(worker: GptPipelineWorker): Promise<void> {
    const actions = this.chapterActions(worker.id);
    if (actions.length)
      return Promise.all(actions.map((run) => run.promise)).then(() => {});
    const existing = this.retries.get(worker.id);
    if (existing) return existing.promise;
    return this.prepareWorker(worker, async (signal) => {
      const running = this.workers.get(worker.id);
      running?.controller.abort();
      await running?.promise;
      for (const s of this.sessions.values()) {
        if (signal.aborted) return;
        if (s.workerId !== worker.id || s.phase === 'completed') continue;
        const session = await this.requireSession(s.id);
        if (signal.aborted) return;
        const next =
          session.phase === 'reviewing'
            ? reopenDeltaReviewRetryWindow(session)
            : session.phase === 'translating'
              ? reopenDeltaRetryWindow(session)
              : session;
        next.revision = session.revision + 1;
        await this.save(next);
      }
    });
  }

  private prepareWorker(
    worker: GptPipelineWorker,
    prepare: (signal: AbortSignal) => Promise<void>,
  ): Promise<void> {
    const preparing = {
      controller: new AbortController(),
      promise: Promise.resolve(),
    };
    this.retries.set(worker.id, preparing);
    this.options.onWorkerChange?.(
      worker.id,
      true,
      this.activeCount(worker.id),
      worker.concurrency,
    );
    preparing.promise = (async () => {
      await prepare(preparing.controller.signal);
      if (!preparing.controller.signal.aborted) await this.beginWorker(worker);
    })().finally(() => {
      this.retries.delete(worker.id);
      this.options.onWorkerChange?.(
        worker.id,
        this.isRunning(worker.id),
        this.activeCount(worker.id),
        worker.concurrency,
      );
      this.clearIdleSources();
    });
    return preparing.promise;
  }
  private prepareChapter(
    worker: GptPipelineWorker,
    prepare: (signal: AbortSignal) => Promise<void>,
    chapter: DeltaSessionSummary,
  ): Promise<void> {
    const snapshot = cloneDeep(worker);
    let slots = this.chapterSlots.get(worker.id);
    if (!slots) {
      slots = new Semaphore(Math.min(snapshot.concurrency ?? 1, this.window));
      this.chapterSlots.set(worker.id, slots);
    }
    const run: ChapterRun = {
      workerId: worker.id,
      controller: new AbortController(),
      promise: Promise.resolve(),
      targetSessionId: chapter.id,
      current: 0,
      max: slots.max,
      attempted: new Set(),
      errors: [],
    };
    this.chapterRuns.set(chapter.id, run);
    // Reserve queued actions too, so neither a second click nor another Worker
    // can take this chapter while it waits for capacity or prepares its retry.
    this.claimed.add(chapter.id);
    this.claimedTasks.set(chapter.id, chapter.identity.taskId);
    this.updateWorker(worker.id, run);
    run.promise = slots
      .use(async () => {
        if (run.controller.signal.aborted) return;
        await prepare(run.controller.signal);
        if (run.controller.signal.aborted) return;
        this.claimed.delete(chapter.id);
        await this.slot(snapshot, run);
        if (run.errors.length) throw run.errors[0];
      })
      .finally(async () => {
        this.claimed.delete(chapter.id);
        this.claimedTasks.delete(chapter.id);
        try {
          // A cancelled/failed preparation may have been the last reservation
          // keeping an otherwise completed task open.
          await this.completeTasks();
        } finally {
          this.chapterRuns.delete(chapter.id);
          if (!this.chapterActions(worker.id).length)
            this.chapterSlots.delete(worker.id);
          this.updateWorker(worker.id, run);
          this.clearIdleSources();
        }
      });
    return run.promise;
  }

  private clearIdleSources() {
    if (!this.workers.size && !this.retries.size && !this.chapterRuns.size)
      this.sources.clear();
  }

  /** A completed or discarded task no longer needs its TOC/glossary adapter. */
  private releaseTaskSources(taskId: string) {
    for (const key of [...this.sources.keys()]) {
      try {
        if (JSON.parse(key)[0] === taskId) this.sources.delete(key);
      } catch {
        this.sources.delete(key);
      }
    }
  }

  async clear(): Promise<void> {
    await this.stopAll();
    await this.options.store.clear();
    this.sessions.clear();
    this.done.clear();
    this.sources.clear();
    this.options.onSessionEvent?.({ type: 'reset', sessions: [] });
  }

  discardChapter(sessionId: string): Promise<void> {
    return this.discard((session) => session.id === sessionId);
  }

  /** Remove one task's checkpoints without blocking unrelated running tasks. */
  discardTask(taskId: string): Promise<void> {
    return this.discardScoped(
      (session) =>
        session.identity.taskId.split('?')[0] === taskId.split('?')[0],
      taskId,
    );
  }

  /** Remove one chapter checkpoint without blocking unrelated running tasks. */
  discardChapterByTask(taskId: string, chapterId: string): Promise<void> {
    return this.discardScoped(
      (session) =>
        session.identity.taskId.split('?')[0] === taskId.split('?')[0] &&
        session.identity.chapterId === chapterId,
      taskId,
    );
  }

  discardNovel(novelId: string): Promise<void> {
    return this.discard((session) => session.identity.novelId === novelId);
  }

  discardPending(): Promise<void> {
    return this.discard((session) => session.phase !== 'completed');
  }

  async retryReview(
    sessionId: string,
    worker: GptPipelineWorker,
  ): Promise<void> {
    if (this.chapterActionBlocked(worker.id, sessionId))
      throw new Error('Delta 正在运行');
    const summary = this.sessions.get(sessionId);
    if (
      !summary ||
      summary.workerId !== worker.id ||
      summary.phase !== 'reviewing' ||
      summary.reviewCompleted ||
      !summary.pauseReason
    )
      throw new Error('审校断点或绑定工作不存在');
    await this.prepareChapter(
      worker,
      async (signal) => {
        const session = await this.requireSession(sessionId);
        if (signal.aborted) return;
        await this.validate(
          session,
          worker,
          await this.source(session.identity.taskId),
        );
        if (signal.aborted) return;
        const next = reopenDeltaReviewRetryWindow(session);
        next.revision = session.revision + 1;
        await this.save(next);
      },
      summary,
    );
  }

  async skipReview(
    sessionId: string,
    worker: GptPipelineWorker,
  ): Promise<void> {
    if (this.chapterActionBlocked(worker.id, sessionId))
      throw new Error('请先停止该工作，再跳过审校');
    const summary = this.sessions.get(sessionId);
    if (!summary || summary.workerId !== worker.id)
      throw new Error('审校断点或绑定工作不存在');
    await this.prepareChapter(
      worker,
      async (signal) => {
        const session = await this.requireSession(sessionId);
        if (signal.aborted) return;
        await this.validate(
          session,
          worker,
          await this.source(session.identity.taskId),
        );
        if (signal.aborted) return;
        const next = skipDeltaReview(session);
        next.revision = session.revision + 1;
        await this.save(next);
      },
      summary,
    );
  }

  async restartChapter(
    sessionId: string,
    worker: GptPipelineWorker,
  ): Promise<void> {
    if (this.chapterActionBlocked(worker.id, sessionId))
      throw new Error('请先停止该工作，再重新翻译');
    const summary = this.sessions.get(sessionId);
    if (!summary || summary.workerId !== worker.id)
      throw new Error('章节断点或绑定工作不存在');
    await this.prepareChapter(
      worker,
      async (signal) => {
        const previous = await this.requireSession(sessionId);
        if (signal.aborted) return;
        const src = await this.source(previous.identity.taskId);
        const current = await src.readChapter(previous.identity.chapterId);
        if (signal.aborted) return;
        const session = createDeltaSession({
          runId: uuidv4(),
          workerId: worker.id,
          identity: {
            ...previous.identity,
            taskCreatedAt:
              this.options
                .jobs()
                .find((job) => job.task === previous.identity.taskId)
                ?.createAt ?? previous.identity.taskCreatedAt,
          },
          source: current.paragraphs,
          glossary: current.glossary,
          glossaryId: current.glossaryId,
          metadata: {
            ...src.metadata,
            chapterTitle: previous.metadata.chapterTitle,
          },
          model: deltaWorkerModel(worker),
          deltaMode: worker.deltaMode,
          previousTranslationHash: translationHash(current, true),
          reviewOptions: normalizeDeltaReviewOptions(worker.deltaReview),
        });
        session.revision = previous.revision + 1;
        await this.save(session);
        for (const key of this.done) {
          if (JSON.parse(key)[0] === sessionId) this.done.delete(key);
        }
      },
      summary,
    );
  }

  private async discardScoped(
    matches: (session: DeltaSessionSummary) => boolean,
    taskId: string,
  ): Promise<void> {
    if (this.hasTask(taskId)) throw new Error('任务正在翻译中');
    const removed: string[] = [];
    try {
      for (const session of this.sessions.values()) {
        if (!matches(session)) continue;
        await this.options.store.delete(session.id);
        this.sessions.delete(session.id);
        removed.push(session.id);
        for (const key of this.done) {
          if (JSON.parse(key)[0] === session.id) this.done.delete(key);
        }
        if (session.phase !== 'completed')
          this.options.onChapterChange?.(
            session.identity.taskId,
            session.identity.chapterId,
            'pending',
          );
      }
    } finally {
      this.releaseTaskSources(taskId);
      if (removed.length)
        this.options.onSessionEvent?.({ type: 'remove', ids: removed });
    }
  }

  private async discard(
    matches: (session: DeltaSessionSummary) => boolean,
  ): Promise<void> {
    if (this.workers.size || this.retries.size || this.chapterRuns.size)
      throw new Error('Delta 正在运行');
    const removed: string[] = [];
    try {
      for (const session of this.sessions.values()) {
        if (!matches(session)) continue;
        await this.options.store.delete(session.id);
        this.sessions.delete(session.id);
        removed.push(session.id);
        for (const key of this.done) {
          if (JSON.parse(key)[0] === session.id) this.done.delete(key);
        }
        if (session.phase !== 'completed')
          this.options.onChapterChange?.(
            session.identity.taskId,
            session.identity.chapterId,
            'pending',
          );
      }
    } finally {
      if (removed.length)
        this.options.onSessionEvent?.({ type: 'remove', ids: removed });
    }
  }

  private source(taskId: string) {
    const generation = this.options
      .jobs()
      .find((job) => job.task === taskId)?.createAt;
    const key = JSON.stringify([taskId, generation]);
    let pending = this.sources.get(key);
    if (!pending) {
      pending = this.options.source(taskId);
      this.sources.set(key, pending);
      pending.catch(() => this.sources.delete(key));
    }
    return pending;
  }
  private updateWorker(id: string, run: WorkerRun) {
    this.options.onWorkerChange?.(
      id,
      this.isRunning(id),
      this.activeCount(id),
      run.max,
    );
  }
  private async save(session: DeltaChapterSession) {
    await this.options.store.save(session);
    const summary = summarizeDeltaSession(session);
    this.sessions.set(session.id, summary);
    this.options.onSessionEvent?.({ type: 'upsert', session: summary });
  }
  private async patch(
    session: DeltaChapterSession,
    changes: Partial<DeltaChapterSession>,
  ) {
    const next = { ...session, ...changes, revision: session.revision + 1 };
    await this.save(next);
    return next;
  }

  /** Never block a finished chapter because the optional converter failed. */
  private async simplifyOutput(session: DeltaChapterSession) {
    if (!this.options.simplifyText || session.phase !== 'readyToSubmit')
      return session;
    try {
      const patch = await simplifyDeltaOutput(
        session,
        this.options.simplifyText,
      );
      return patch ? await this.patch(session, patch) : session;
    } catch (error) {
      console.warn('Delta 简繁后处理失败，保留模型原文', error);
      return session;
    }
  }

  private async claim(
    worker: GptPipelineWorker,
    run: WorkerRun,
  ): Promise<ChapterClaim | undefined> {
    // Reservation happens synchronously before any chapter content is read.
    for (const session of this.sessions.values()) {
      if (
        session.workerId !== worker.id ||
        (run.targetTaskId && session.identity.taskId !== run.targetTaskId) ||
        (run.targetSessionId && session.id !== run.targetSessionId) ||
        session.phase === 'completed' ||
        run.attempted.has(session.id) ||
        this.claimed.has(session.id)
      )
        continue;
      this.claimed.add(session.id);
      run.attempted.add(session.id);
      return {
        session,
        taskId: session.identity.taskId,
        chapterId: session.identity.chapterId,
      };
    }
    if (run.targetSessionId) return;
    for (const job of this.options.jobs()) {
      if (run.targetTaskId && job.task !== run.targetTaskId) continue;
      if (job.finishAt) continue;
      let src: DeltaTaskSource;
      try {
        src = await this.source(job.task);
      } catch (error) {
        run.errors.push(error);
        continue;
      }
      if (run.controller.signal.aborted) return;
      const excluded = new Set(job.excludedChapters ?? []);
      for (const chapter of src.chapters) {
        if (chapter.status === 'done' || excluded.has(chapter.chapterId))
          continue;
        const identity = {
          taskId: job.task,
          taskCreatedAt: job.createAt,
          novelId: src.novelId,
          volumeId: src.volumeId,
          chapterId: chapter.chapterId,
          title: chapter.title,
        };
        const id = deltaChapterKey(identity);
        const saved = this.sessions.get(id);
        if (
          this.claimed.has(id) ||
          run.attempted.has(id) ||
          this.done.has(doneKey(job, id)) ||
          (saved && saved.phase !== 'completed')
        )
          continue;
        if (
          saved?.phase === 'completed' &&
          saved.identity.taskId === job.task &&
          saved.identity.taskCreatedAt === job.createAt
        ) {
          this.done.add(doneKey(job, id));
          continue;
        }
        this.claimed.add(id);
        run.attempted.add(id);
        return {
          identity,
          taskId: job.task,
          chapterId: chapter.chapterId,
          title: chapter.title,
        };
      }
    }
  }

  private async slot(worker: GptPipelineWorker, run: WorkerRun) {
    while (!run.controller.signal.aborted) {
      // One shared window across workers and explicit chapter actions. Reserving
      // a slot before claiming keeps paused/queued sessions out of memory too.
      await this.globalSlots.acquire();
      let item: ChapterClaim | undefined;
      try {
        if (run.controller.signal.aborted) {
          this.globalSlots.release();
          break;
        }
        item = await this.claim(worker, run);
      } catch (error) {
        this.globalSlots.release();
        throw error;
      }
      if (!item) {
        this.globalSlots.release();
        break;
      }
      const id =
        'session' in item ? item.session.id : deltaChapterKey(item.identity);
      this.claimedTasks.set(id, item.taskId);
      run.current++;
      this.updateWorker(worker.id, run);
      let src: DeltaTaskSource | undefined;
      try {
        this.options.onChapterChange?.(
          item.taskId,
          item.chapterId,
          'translating',
        );
        src = await this.source(item.taskId);
        let session: DeltaChapterSession;
        if ('session' in item)
          session = await this.requireSession(item.session.id);
        else {
          const current = await src.readChapter(item.chapterId);
          session = createDeltaSession({
            runId: uuidv4(),
            workerId: worker.id,
            identity: item.identity,
            source: current.paragraphs,
            glossary: current.glossary,
            glossaryId: current.glossaryId,
            metadata: { ...src.metadata, chapterTitle: item.title },
            model: deltaWorkerModel(worker),
            deltaMode: worker.deltaMode,
            previousTranslationHash: translationHash(current, true),
            reviewOptions: normalizeDeltaReviewOptions(worker.deltaReview),
          });
          const old = this.sessions.get(session.id);
          if (old) session.revision = old.revision + 1;
          await this.save(session);
        }
        await this.execute(
          session,
          worker,
          src,
          run.controller.signal,
          !('session' in item),
        );
      } catch (error) {
        // A failed transaction must not be replaced by stale in-memory progress.
        const saved = await this.options.store.get(id);
        if (saved) {
          this.sessions.set(id, summarizeDeltaSession(saved));
          await this.patch(saved, { pauseReason: String(error) });
        } else run.errors.push(error);
        this.options.onChapterChange?.(
          item.taskId,
          item.chapterId,
          saved?.pauseReason?.includes('停止')
            ? 'pending'
            : saved
              ? 'paused'
              : 'error',
          saved,
        );
      } finally {
        // Per-chapter adapters snapshot the glossary and can capture a whole
        // TOC; only the current run needs them.
        src?.disposeChapter?.(item.chapterId);
        this.claimed.delete(id);
        this.claimedTasks.delete(id);
        run.current--;
        this.updateWorker(worker.id, run);
        this.globalSlots.release();
      }
    }
  }

  private async validate(
    session: DeltaChapterSession,
    worker: GptPipelineWorker,
    src: DeltaTaskSource,
  ) {
    const current = await src.readChapter(session.identity.chapterId);
    if (
      session.schemaVersion !== 1 ||
      (session.promptVersion !== DELTA_PROMPT_VERSION &&
        !(
          session.promptVersion === 3 && session.reviewOptions === undefined
        )) ||
      deltaFingerprint(deltaWorkerModel(worker)) !==
        deltaFingerprint(session.model) ||
      (session.phase === 'translating' &&
        (session.deltaMode ?? 'delta') !== (worker.deltaMode ?? 'delta'))
    )
      throw new Error('模型、协议或提示配置已变化，请保留草稿或放弃后重新启动');
    if (
      session.reviewOptions !== undefined &&
      deltaFingerprint(normalizeDeltaReviewOptions(session.reviewOptions)) !==
        deltaFingerprint(normalizeDeltaReviewOptions(worker.deltaReview))
    )
      throw new Error('审校配置已变化，请保留草稿或放弃后重新启动');
    if (
      deltaFingerprint(current.paragraphs) !== session.sourceHash ||
      current.glossaryId !== session.glossaryId ||
      deltaFingerprint(sortedGlossary(current.glossary)) !==
        deltaFingerprint(sortedGlossary(session.glossary))
    )
      throw new Error('原文或术语已变化，请放弃断点后重新启动');
    return current;
  }

  private async execute(
    session: DeltaChapterSession,
    worker: GptPipelineWorker,
    src: DeltaTaskSource,
    signal: AbortSignal,
    fresh = false,
  ) {
    if (session.phase === 'translating') {
      if (!fresh) await this.validate(session, worker, src);
      if (signal.aborted) {
        await this.patch(session, { pauseReason: '用户停止' });
        return;
      }
      session = await (this.options.runChapter ?? runDeltaChapter)(session, {
        store: { save: async (s) => this.save(s) },
        transport: createPiAiSession,
        key: worker.key,
        signal,
      });
      if (session.pauseReason) {
        if (
          this.sessions.get(session.id)?.revision !== session.revision ||
          this.sessions.get(session.id)?.pauseReason !== session.pauseReason
        )
          await this.patch(session, { pauseReason: session.pauseReason });
        this.options.onChapterChange?.(
          session.identity.taskId,
          session.identity.chapterId,
          signal.aborted || session.pauseReason.includes('停止')
            ? 'pending'
            : 'paused',
          session,
        );
        return;
      }
      // A custom runner and the normal runner share the same pre-submit boundary.
      if (this.sessions.get(session.id)?.revision !== session.revision)
        await this.save(session);
    }
    // Older checkpoints may have sent image-only pages into Review.
    // Keep their request history, but resume through the normal submission path.
    if (session.phase === 'reviewing' && !session.batches.length)
      session = await this.patch(session, {
        phase: 'readyToSubmit',
        pauseReason: undefined,
        review: session.review
          ? { ...session.review, skipped: true }
          : undefined,
      });
    if (
      session.phase === 'readyToSubmit' &&
      session.batches.length > 0 &&
      session.reviewOptions?.enabled &&
      !session.review?.completed &&
      !session.review?.skipped
    )
      session = await this.patch(session, { phase: 'reviewing' });
    if (session.phase === 'reviewing') {
      await this.validate(session, worker, src);
      if (signal.aborted) {
        await this.patch(session, { pauseReason: '用户停止' });
        return;
      }
      session = await (this.options.runReview ?? runDeltaReview)(session, {
        store: { save: async (s) => this.save(s) },
        transport: createPiAiSession,
        key: worker.key,
        signal,
        readContext: (offset, contextSignal) =>
          src.readChapterContext(
            session.identity.chapterId,
            offset,
            contextSignal,
          ),
      });
      if (this.sessions.get(session.id)?.revision !== session.revision)
        await this.save(session);
      if (session.pauseReason || session.phase !== 'readyToSubmit') {
        this.options.onChapterChange?.(
          session.identity.taskId,
          session.identity.chapterId,
          signal.aborted || session.pauseReason?.includes('停止')
            ? 'pending'
            : 'paused',
          session,
        );
        return;
      }
    }
    if (signal.aborted && session.phase !== 'submitting') {
      await this.patch(session, { pauseReason: '用户停止' });
      return;
    }
    const current = await this.validate(session, worker, src);
    session = await this.simplifyOutput(session);
    const final = session.finalParagraphs!;
    const finalFingerprint = deltaFingerprint({
      paragraphs: final,
      glossary: sortedGlossary(session.glossary),
    });
    const currentFingerprint = translationHash(current);
    if (currentFingerprint === finalFingerprint) {
      await this.finish(session);
      return;
    }
    if (translationHash(current, true) !== session.previousTranslationHash)
      throw new Error('既有译文已变化，已暂停以保留新的译文');
    signal.throwIfAborted();
    session = await this.patch(session, {
      phase: 'submitting',
      pauseReason: undefined,
    });
    // Once sent, upload drains even if this worker is stopped. An ambiguous result
    // stays submitting and is read back before any later retry.
    await src.uploadChapter(
      session.identity.chapterId,
      session.glossaryId,
      final,
    );
    await this.finish(session);
  }

  private async finish(session: DeltaChapterSession) {
    const completed = await this.patch(session, {
      phase: 'completed',
      pauseReason: undefined,
      completedAt: Date.now(),
    });
    for (const job of this.options.jobs()) {
      if (
        job.task === session.identity.taskId &&
        session.identity.taskCreatedAt !== undefined &&
        job.createAt !== session.identity.taskCreatedAt
      )
        continue;
      let src: DeltaTaskSource;
      try {
        src = await this.source(job.task);
      } catch {
        continue;
      }
      if (
        src.novelId === session.identity.novelId &&
        src.volumeId === session.identity.volumeId
      )
        this.done.add(doneKey(job, session.id));
    }
    this.options.onChapterChange?.(
      session.identity.taskId,
      session.identity.chapterId,
      'done',
      completed,
    );
  }
  private async completeTasks(taskId?: string) {
    let failure: unknown;
    for (const job of this.options.jobs()) {
      if (taskId && job.task !== taskId) continue;
      if (
        job.finishAt ||
        this.taskHasDrafts(job.task) ||
        this.hasTask(job.task)
      )
        continue;
      let src: DeltaTaskSource;
      try {
        src = await this.source(job.task);
      } catch (error) {
        failure ??= error;
        continue;
      }
      const excluded = new Set(job.excludedChapters ?? []);
      const chapters = src.chapters.filter(
        (chapter) => !excluded.has(chapter.chapterId),
      );
      if (
        chapters.every(
          (c) =>
            c.status === 'done' ||
            this.done.has(
              doneKey(
                job,
                deltaChapterKey({
                  taskId: job.task,
                  novelId: src.novelId,
                  volumeId: src.volumeId,
                  chapterId: c.chapterId,
                }),
              ),
            ),
        )
      ) {
        const chapterIds = new Set(
          chapters.map((chapter) => chapter.chapterId),
        );
        for (const session of this.sessions.values()) {
          if (
            session.identity.taskId === job.task &&
            session.identity.taskCreatedAt === job.createAt &&
            !excluded.has(session.identity.chapterId)
          )
            chapterIds.add(session.identity.chapterId);
        }
        this.options.onTaskDone?.(job.task, chapterIds.size);
        this.releaseTaskSources(job.task);
      }
    }
    if (failure) throw failure;
  }
}
