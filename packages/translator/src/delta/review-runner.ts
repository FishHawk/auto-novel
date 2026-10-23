import type { ToolCall } from '@earendil-works/pi-ai';
import { assembleDeltaChapter } from './batch';
import { deltaFingerprint, retryWait } from './chapter-runner';
import type { RunDeltaChapterOptions } from './chapter-runner';
import {
  buildReviewSystemPrompt,
  buildReviewTask,
  deltaReviewTools,
  DELTA_REVIEW_PROMPT_VERSION,
  supportsDeltaReviewIssueKinds,
} from './review-prompt';
import {
  deltaReviewCategories,
  deltaReviewContextOffsets,
  deltaReviewIssueKinds,
} from './types';
import type {
  DeltaChapterContext,
  DeltaChapterSession,
  DeltaReviewOptions,
  DeltaReviewIssue,
  DeltaSubmitReview,
} from './types';

export interface RunDeltaReviewOptions extends RunDeltaChapterOptions {
  readContext?: (
    offset: number,
    signal?: AbortSignal,
  ) => Promise<DeltaChapterContext>;
}

export function normalizeDeltaReviewOptions(
  options?: DeltaReviewOptions,
): DeltaReviewOptions {
  return {
    enabled: options?.enabled ?? false,
    mode: options?.mode ?? 'new',
  };
}

function initialize(session: DeltaChapterSession) {
  const options = normalizeDeltaReviewOptions(session.reviewOptions);
  const append = options.mode === 'append';
  session.review = {
    id: `${session.runId}:review`,
    promptVersion:
      append && !supportsDeltaReviewIssueKinds(session.tools)
        ? 2
        : DELTA_REVIEW_PROMPT_VERSION,
    options,
    draftHash: deltaFingerprint(session.translations),
    originalTranslations: structuredClone(session.translations),
    systemPrompt: append
      ? session.systemPrompt
      : buildReviewSystemPrompt(session),
    tools: structuredClone(append ? session.tools : deltaReviewTools),
    messages: append ? structuredClone(session.messages) : [],
    initialMessageCount: append ? session.messages.length : 0,
    requests: [],
    attempts: 0,
    failures: 0,
    totalAttempts: 0,
    window: 1,
    contextReads: 0,
    completed: false,
  };
  session.review.messages.push({
    role: 'user',
    content: buildReviewTask(session),
    timestamp: Date.now(),
  });
}

export function reopenDeltaReviewRetryWindow(
  session: DeltaChapterSession,
): DeltaChapterSession {
  const next = structuredClone(session);
  if (!next.review) next.pauseReason = undefined;
  if (next.review && !next.review.completed) {
    next.review.window++;
    next.review.attempts = 0;
    next.review.failures = 0;
    next.review.contextReads = 0;
    next.pauseReason = undefined;
  }
  return next;
}

/** A user may explicitly keep the current draft and bypass an unfinished/uncertain Review. */
export function skipDeltaReview(
  session: DeltaChapterSession,
): DeltaChapterSession {
  if (session.phase !== 'reviewing') throw new Error('仅审校阶段可以跳过');
  const next = structuredClone(session);
  if (!next.review) initialize(next);
  if (next.review!.pendingAssistant)
    throw new Error('请先继续结算已收到的审校回复');
  next.review!.skipped = true;
  next.phase = 'readyToSubmit';
  next.pauseReason = undefined;
  return next;
}

export function isBlockingDeltaReviewIssue(issue: DeltaReviewIssue): boolean {
  return issue.kind !== 'cross_chapter_consistency';
}

function validateReview(
  session: DeltaChapterSession,
  value: unknown,
): DeltaSubmitReview {
  const review = session.review!;
  if (!value || typeof value !== 'object')
    throw new Error('submit_review参数必须为对象');
  const args = value as DeltaSubmitReview;
  if (
    Object.keys(args).some(
      (k) => !['reviewId', 'draftHash', 'changes', 'unresolved'].includes(k),
    )
  )
    throw new Error('submit_review包含未知字段');
  if (
    args.reviewId !== review.id ||
    args.draftHash !== review.draftHash ||
    deltaFingerprint(session.translations) !== review.draftHash
  )
    throw new Error('审校ID或译稿指纹不匹配');
  if (!Array.isArray(args.changes) || !Array.isArray(args.unresolved))
    throw new Error('changes/unresolved必须为数组');
  const validIds = new Set(
    session.paragraphs.filter((p) => !p.empty).map((p) => p.id),
  );
  for (const entries of [args.changes, args.unresolved]) {
    const seen = new Set<string>();
    for (const entry of entries) {
      if (
        !entry ||
        typeof entry !== 'object' ||
        !validIds.has(entry.paragraphId) ||
        seen.has(entry.paragraphId)
      )
        throw new Error('审校段落ID无效或重复');
      seen.add(entry.paragraphId);
      if (typeof entry.reason !== 'string' || !entry.reason.trim())
        throw new Error('必须提供具体审校依据');
      const keys =
        entries === args.changes
          ? ['paragraphId', 'translation', 'category', 'reason']
          : ['paragraphId', 'reason', 'kind'];
      if (Object.keys(entry).some((k) => !keys.includes(k)))
        throw new Error('审校条目包含未知字段');
      if (entries === args.unresolved) {
        const issue = entry as DeltaReviewIssue;
        if (
          issue.kind !== undefined &&
          !deltaReviewIssueKinds.includes(issue.kind)
        )
          throw new Error('未知审校问题类型');
      }
    }
  }
  for (const edit of args.changes) {
    if (
      typeof edit.translation !== 'string' ||
      !edit.translation.trim() ||
      /[\r\n\u2028\u2029]/.test(edit.translation)
    )
      throw new Error('审校译文必须非空且不能包含换行');
    if (!deltaReviewCategories.includes(edit.category))
      throw new Error('未知审校分类');
  }
  return args;
}

/** One bounded review, independent of translation retries. Complete replies settle before any new request. */
export async function runDeltaReview(
  initial: DeltaChapterSession,
  options: RunDeltaReviewOptions,
): Promise<DeltaChapterSession> {
  let session = structuredClone(initial);
  if (session.phase !== 'reviewing') return session;
  async function commit(change: (next: DeltaChapterSession) => void) {
    const next = structuredClone(session);
    change(next);
    next.revision++;
    // The store owns its snapshot; retaining a second clone here doubles the
    // transient peak for every checkpoint on large transcripts.
    await options.store.save(next);
    session = next;
    options.onUpdate?.(structuredClone(session));
  }
  async function pause(reason: string) {
    await commit((next) => {
      next.pauseReason = reason;
    });
    return session;
  }
  if (!session.review) await commit(initialize);

  async function settlePending(): Promise<boolean> {
    const review = session.review!;
    const pending = review.pendingAssistant!;
    const message = pending.message;
    const calls = message.content.filter(
      (b): b is ToolCall => b.type === 'toolCall',
    );
    const contextCall = calls.find(
      (call) => call.name === 'get_chapter_context',
    );
    const deferSubmission =
      calls.length === 2 &&
      contextCall !== undefined &&
      calls.some((call) => call.name === 'submit_review');
    // A submission generated alongside a read cannot use that read's result.
    const call = deferSubmission ? contextCall! : calls[0];
    let error: string | undefined;
    let result: DeltaSubmitReview | undefined;
    let context: DeltaChapterContext | undefined;
    if (message.stopReason === 'length')
      error = '审校输出截断，未执行任何工具。请完整提交较少的必要修改。';
    else if (calls.length !== 1 && !deferSubmission)
      error = '审校每轮必须恰好调用一个工具，不能混合或重复提交。';
    else if (call.name === 'submit_review') {
      try {
        result = validateReview(session, call.arguments);
      } catch (e) {
        error = String(e);
      }
    } else if (call.name === 'get_chapter_context') {
      const args = call.arguments as { offset?: number } | null;
      if (
        !args ||
        Object.keys(args).length !== 1 ||
        !Number.isInteger(args.offset) ||
        !deltaReviewContextOffsets.some((offset) => offset === args.offset)
      )
        error = 'offset只允许-2、-1、1、2。';
      else if (review.contextReads >= 4)
        error =
          '已达到4次上下文读取上限，请直接submit_review；不能确定的问题列入unresolved。';
      else {
        if (options.signal?.aborted) {
          await pause('用户已停止，审校断点已保存。');
          return false;
        }
        await commit((next) => {
          next.review!.contextReads++;
        });
        try {
          context = options.readContext
            ? await options.readContext(args.offset!, options.signal)
            : { available: false, reason: '该来源不支持相邻章节读取' };
          // Never silently truncate a chapter and present it as complete context.
          if (
            context.available &&
            context.source.join('\n').length +
              (context.translation?.join('\n').length ?? 0) >
              40000
          )
            context = {
              available: false,
              reason: '相邻章超过40000字符上下文上限，本次未返回文本',
            };
        } catch (e) {
          if (options.signal?.aborted || (e as Error).name === 'AbortError') {
            await pause('用户已停止，审校断点已保存。');
            return false;
          }
          context = {
            available: false,
            reason: `相邻章节读取失败：${String(e)}`,
          };
        }
      }
    } else error = '审校阶段只允许submit_review或get_chapter_context。';
    const deferredFeedback =
      '本次 submit_review 未执行，译稿未修改。请先阅读 get_chapter_context 返回的上下文或不可用原因，下一轮重新判断并单独提交审校结果；无需修改可提交 changes: []，无需疑问项可提交 unresolved: []。不要直接沿用本轮尚未参考读取结果的修改。';
    await commit((next) => {
      const r = next.review!;
      const record = r.requests.find(
        (item) => item.requestId === pending.requestId,
      )!;
      record.usage = message.usage;
      record.usagePresence = pending.usagePresence;
      record.status = error || deferSubmission ? 'rejected' : 'accepted';
      record.error = error ?? (deferSubmission ? deferredFeedback : undefined);
      r.messages.push(message);
      const feedback = error
        ? `${error} 本次未修改译稿。请修正工具参数；reviewId=${r.id}，draftHash=${r.draftHash}。`
        : result
          ? '审校结果已保存，审校结束。'
          : JSON.stringify(context);
      for (const item of calls) {
        const deferred =
          deferSubmission && item.name === 'submit_review' && !error;
        r.messages.push({
          role: 'toolResult',
          toolCallId: item.id,
          toolName: item.name,
          content: [
            {
              type: 'text',
              text: deferred ? deferredFeedback : feedback,
            },
          ],
          isError: !!error || deferred,
          timestamp: Date.now(),
        });
      }
      if (!calls.length)
        r.messages.push({
          role: 'user',
          content: feedback,
          timestamp: Date.now(),
        });
      r.pendingAssistant = undefined;
      if (error) {
        r.failures++;
        return;
      }
      if (result) {
        r.changes = result.changes;
        r.unresolved = result.unresolved;
        r.completed = true;
        for (const edit of result.changes)
          next.translations[edit.paragraphId] = edit.translation;
        next.finalParagraphs = assembleDeltaChapter(next);
        next.finalHash = deltaFingerprint(next.finalParagraphs);
        const blockingCount = result.unresolved.filter(
          isBlockingDeltaReviewIssue,
        ).length;
        next.phase = blockingCount ? 'reviewing' : 'readyToSubmit';
        next.pauseReason = blockingCount
          ? `审校有${blockingCount}项待确认问题，请查看后选择跳过或重新翻译。`
          : undefined;
      }
    });
    return true;
  }

  if (session.review!.pendingAssistant && !(await settlePending()))
    return session;
  if (session.review!.completed || session.review!.skipped) return session;
  if (session.pauseReason && !session.pauseReason.includes('停止'))
    return session;
  while (session.phase === 'reviewing') {
    if (options.signal?.aborted) return pause('用户已停止，审校断点已保存。');
    const r = session.review!;
    if (r.failures >= 3 || r.attempts >= 7)
      return pause('审校已达到本窗口请求上限，请显式重试或跳过。');
    await commit((next) => {
      const review = next.review!;
      next.pauseReason = undefined;
      review.attempts++;
      review.totalAttempts++;
      review.requests.push({
        requestId: `${review.id}:${review.totalAttempts}`,
        batchId: 'review',
        window: review.window,
        attempt: review.attempts,
        status: 'pending',
      });
    });
    if (options.signal?.aborted) {
      await commit((next) => {
        next.review!.requests.pop();
        next.review!.attempts--;
        next.review!.totalAttempts--;
        next.pauseReason = '用户已停止，审校断点已保存。';
      });
      return session;
    }
    const records = session.review!.requests;
    const requestId = records[records.length - 1].requestId;
    let response;
    try {
      response = await options.transport({
        ...session.model,
        key: options.key,
        systemPrompt: session.review!.systemPrompt,
        messages: structuredClone(session.review!.messages),
        tools: structuredClone(session.review!.tools),
        signal: options.signal,
        onThinking: options.onThinking,
      });
    } catch (e) {
      const failure = e as Error & { status?: number; retryAfter?: number };
      const aborted = options.signal?.aborted || failure.name === 'AbortError';
      const retryable =
        !aborted &&
        (failure.status === undefined ||
          failure.status === 429 ||
          (failure.status >= 500 && failure.status <= 599)) &&
        !/unsupported|not supported|context.{0,20}(?:limit|long|exceed)|不支持/i.test(
          failure.message,
        );
      await commit((next) => {
        const review = next.review!;
        const record = review.requests.find(
          (item) => item.requestId === requestId,
        )!;
        record.status = 'error';
        record.error = failure.message;
        review.failures++;
        if (aborted) next.pauseReason = '用户已停止，审校断点已保存。';
        else if (!retryable) next.pauseReason = failure.message;
      });
      if (session.pauseReason) return session;
      try {
        await retryWait(
          Math.min(
            30000,
            Math.max(
              0,
              failure.retryAfter ??
                (options.retryDelayMs ?? 500) *
                  2 ** (session.review!.failures - 1),
            ),
          ),
          options.signal,
        );
      } catch {
        return pause('用户已停止，审校断点已保存。');
      }
      continue;
    }
    await commit((next) => {
      next.review!.pendingAssistant = {
        requestId,
        message: response.message,
        usagePresence: response.usagePresence,
      };
      next.review!.requests.find(
        (item) => item.requestId === requestId,
      )!.status = 'received';
    });
    if (!(await settlePending())) return session;
    if (session.review!.completed) return session;
  }
  return session;
}
