import type { DeltaChapterSession } from '@auto-novel/translator';

/** Thinking and provider summaries are diagnostic data, not translation state. */
export const THINKING_RETENTION_MS = 3 * 24 * 60 * 60 * 1000;

interface MessageLike {
  role?: string;
  content?: unknown;
  timestamp?: number;
}

function pruneMessages(
  messages: unknown[],
  cutoff: number,
): { changed: boolean; messages: unknown[] } {
  let changed = false;
  const next: unknown[] = [];
  for (const raw of messages) {
    const message = raw as MessageLike;
    if (
      message?.role !== 'assistant' ||
      !Array.isArray(message.content) ||
      typeof message.timestamp !== 'number' ||
      message.timestamp >= cutoff
    ) {
      next.push(raw);
      continue;
    }
    const content = message.content as { type?: string }[];
    const kept = content.filter((block) => block?.type !== 'thinking');
    if (kept.length === content.length) {
      next.push(raw);
      continue;
    }
    changed = true;
    // A thinking-only assistant turn carries no replayable tool call or text.
    if (kept.length > 0) next.push({ ...message, content: kept });
  }
  return { changed, messages: next };
}

/**
 * Remove thinking blocks older than the retention window in place.
 * Returns true when the session was changed and should be persisted again.
 */
export function pruneSessionThinking(
  session: DeltaChapterSession,
  now = Date.now(),
): boolean {
  const cutoff = now - THINKING_RETENTION_MS;
  let changed = false;
  const messages = pruneMessages(
    Array.isArray(session.messages) ? session.messages : [],
    cutoff,
  );
  if (messages.changed) {
    session.messages = messages.messages as DeltaChapterSession['messages'];
    changed = true;
  }
  const review = session.review;
  if (review) {
    const reviewMessages = pruneMessages(
      Array.isArray(review.messages) ? review.messages : [],
      cutoff,
    );
    if (reviewMessages.changed) {
      review.messages = reviewMessages.messages as typeof review.messages;
      changed = true;
    }
  }
  if (prunePendingThinking(session.pendingAssistant, cutoff)) changed = true;
  if (prunePendingThinking(review?.pendingAssistant, cutoff)) changed = true;
  return changed;
}

function prunePendingThinking(
  pending: { message: { content?: unknown; timestamp?: number } } | undefined,
  cutoff: number,
): boolean {
  if (
    !pending ||
    !Array.isArray(pending.message.content) ||
    typeof pending.message.timestamp !== 'number' ||
    pending.message.timestamp >= cutoff
  )
    return false;
  const content = pending.message.content as { type?: string }[];
  const kept = content.filter((block) => block?.type !== 'thinking');
  if (kept.length === content.length) return false;
  pending.message.content = kept;
  return true;
}

function latestTimestamp(messages: unknown[] | undefined): number {
  let latest = 0;
  for (const raw of messages ?? []) {
    const timestamp = (raw as MessageLike)?.timestamp;
    if (typeof timestamp === 'number' && timestamp > latest) latest = timestamp;
  }
  return latest;
}

function completedBefore(session: DeltaChapterSession, cutoff: number) {
  const completedAt =
    session.completedAt ??
    Math.max(
      latestTimestamp(session.messages),
      latestTimestamp(session.review?.messages),
    );
  return completedAt < cutoff;
}

/** Keep only the originals that are still needed to render review changes. */
function pruneReviewOriginals(session: DeltaChapterSession): boolean {
  const review = session.review;
  if (!review?.originalTranslations) return false;
  const changed = new Set(
    (review.changes ?? []).map((change) => change.paragraphId),
  );
  let pruned = false;
  for (const paragraphId of Object.keys(review.originalTranslations)) {
    if (changed.has(paragraphId)) continue;
    delete review.originalTranslations[paragraphId];
    pruned = true;
  }
  return pruned;
}

/** Keep completed results previewable without retaining their replay history. */
export function compactCompletedSession(session: DeltaChapterSession): boolean {
  if (session.phase !== 'completed') return false;
  let changed = false;
  if (session.messages.length) {
    session.messages = [];
    changed = true;
  }
  if (session.pendingAssistant) {
    session.pendingAssistant = undefined;
    changed = true;
  }
  if (session.systemPrompt) {
    session.systemPrompt = '';
    changed = true;
  }
  if (session.tools?.length) {
    session.tools = [];
    changed = true;
  }
  if (session.source?.length) {
    session.source = [];
    changed = true;
  }
  if (session.finalParagraphs?.length) {
    session.finalParagraphs = undefined;
    changed = true;
  }
  if (Object.keys(session.glossary).length) {
    session.glossary = {};
    changed = true;
  }
  if (session.preferences) {
    session.preferences = '';
    changed = true;
  }
  if (session.metadata.description) {
    session.metadata.description = undefined;
    changed = true;
  }
  const review = session.review;
  if (review) {
    if (review.messages?.length) {
      review.messages = [];
      changed = true;
    }
    if (review.pendingAssistant) {
      review.pendingAssistant = undefined;
      changed = true;
    }
    if (review.systemPrompt) {
      review.systemPrompt = '';
      changed = true;
    }
    if (review.tools?.length) {
      review.tools = [];
      changed = true;
    }
    if (pruneReviewOriginals(session)) changed = true;
  }
  return changed;
}

/**
 * Drop expired diagnostics; unfinished native/append sessions retain the
 * transcript needed to resume, while completed results only need preview data.
 */
export function pruneSessionHistory(
  session: DeltaChapterSession,
  now = Date.now(),
): boolean {
  let changed = pruneSessionThinking(session, now);
  const cutoff = now - THINKING_RETENTION_MS;

  if (session.phase === 'completed' && completedBefore(session, cutoff))
    return compactCompletedSession(session) || changed;

  // Native Delta replays its transcript, so only thinking can be trimmed.
  // DeltaX/DeltaXs rebuild their prompt per request; append Review still
  // reuses the translation transcript and keeps it alive.
  if (
    session.phase !== 'completed' &&
    (session.deltaMode ?? 'delta') !== 'delta' &&
    session.reviewOptions?.mode !== 'append' &&
    !session.pendingAssistant
  ) {
    const recent = session.messages.filter((message) => {
      const timestamp = (message as MessageLike).timestamp;
      return typeof timestamp !== 'number' || timestamp >= cutoff;
    });
    if (recent.length !== session.messages.length) {
      session.messages = recent;
      changed = true;
    }
  }
  return changed;
}
