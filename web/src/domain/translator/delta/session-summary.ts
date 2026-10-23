import type {
  DeltaChapterIdentity,
  DeltaChapterSession,
  DeltaMetadata,
  DeltaModelSnapshot,
  DeltaRequestRecord,
} from '@auto-novel/translator';

export interface DeltaSessionSummary {
  id: string;
  runId: string;
  workerId: string;
  identity: DeltaChapterIdentity;
  revision: number;
  phase: DeltaChapterSession['phase'];
  pauseReason?: string;
  metadata: Pick<DeltaMetadata, 'title' | 'chapterTitle'>;
  model: Pick<DeltaModelSnapshot, 'protocol' | 'endpoint' | 'model'>;
  cursor: number;
  batchCount: number;
  paragraphCount: number;
  completedParagraphs: number;
  totalAttempts: number;
  lastRequest?: Pick<
    DeltaRequestRecord,
    'requestId' | 'batchId' | 'window' | 'attempt' | 'status'
  >;
  requestCount: number;
  usage: Record<
    'input' | 'output' | 'cacheRead' | 'cacheWrite',
    { total: number; reported: number }
  >;
  tokenTotals: { cacheRead: number; output: number };
  reviewCompleted: boolean;
}

export type DeltaSessionEvent =
  | { type: 'reset'; sessions: DeltaSessionSummary[] }
  | { type: 'upsert'; session: DeltaSessionSummary }
  | { type: 'remove'; ids: string[] };

/** Display state owns no chapter text, request history, or mutable session objects. */
export function summarizeDeltaSession(
  session: DeltaChapterSession,
): DeltaSessionSummary {
  const requests = new Map(
    session.requests.map((request) => [request.requestId, request]),
  );
  const usage: DeltaSessionSummary['usage'] = {
    input: { total: 0, reported: 0 },
    output: { total: 0, reported: 0 },
    cacheRead: { total: 0, reported: 0 },
    cacheWrite: { total: 0, reported: 0 },
  };
  for (const request of requests.values()) {
    for (const key of ['input', 'output', 'cacheRead', 'cacheWrite'] as const) {
      if (request.usagePresence?.[key] && request.usage) {
        usage[key].total += request.usage[key];
        usage[key].reported++;
      }
    }
  }
  const last = session.requests.at(-1);
  return {
    id: session.id,
    runId: session.runId,
    workerId: session.workerId,
    identity: { ...session.identity },
    revision: session.revision,
    phase: session.phase,
    pauseReason: session.pauseReason,
    metadata: {
      title: session.metadata.title,
      chapterTitle: session.metadata.chapterTitle,
    },
    model: {
      protocol: session.model.protocol,
      endpoint: session.model.endpoint,
      model: session.model.model,
    },
    cursor: session.cursor,
    batchCount: session.batches.length,
    paragraphCount: session.paragraphs.length,
    completedParagraphs: session.paragraphs.filter(
      (paragraph) => paragraph.empty || paragraph.id in session.translations,
    ).length,
    totalAttempts: session.retry.totalAttempts,
    lastRequest: last
      ? {
          requestId: last.requestId,
          batchId: last.batchId,
          window: last.window,
          attempt: last.attempt,
          status: last.status,
        }
      : undefined,
    requestCount: requests.size,
    usage,
    tokenTotals: session.requests.reduce(
      (total, request) => ({
        cacheRead: total.cacheRead + (request.usage?.cacheRead ?? 0),
        output: total.output + (request.usage?.output ?? 0),
      }),
      { cacheRead: 0, output: 0 },
    ),
    reviewCompleted: session.review?.completed ?? false,
  };
}
