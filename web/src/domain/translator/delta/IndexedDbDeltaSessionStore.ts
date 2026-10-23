import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import type { DeltaChapterSession } from '@auto-novel/translator';
import type { DeltaSessionRepository } from './types';
import {
  summarizeDeltaSession,
  type DeltaSessionSummary,
} from './session-summary';
import { pruneSessionHistory } from './thinking-retention';

interface DeltaDatabase extends DBSchema {
  sessions: { key: string; value: DeltaChapterSession };
  summaries: { key: string; value: DeltaSessionSummary };
  meta: { key: string; value: { key: string; value: number | boolean } };
}

// IndexedDB requires a versionchange callback to create object stores. This
// version is fixed at 1 for now; the callback only creates the initial schema
// and is not a migration path for older versions.
const DATABASE_VERSION = 1;
const PRUNE_KEY = 'history-pruned-at';
/** A daily scan is enough for a three-day diagnostic window. */
const PRUNE_INTERVAL_MS = 24 * 60 * 60 * 1000;

function snapshotPending(pending: DeltaChapterSession['pendingAssistant']) {
  return pending && { ...pending, message: { ...pending.message } };
}

/** Own the paths retention mutates; native put snapshots the large transcript. */
function snapshot(session: DeltaChapterSession): DeltaChapterSession {
  return {
    ...session,
    metadata: { ...session.metadata },
    pendingAssistant: snapshotPending(session.pendingAssistant),
    review: session.review && {
      ...session.review,
      pendingAssistant: snapshotPending(session.review.pendingAssistant),
      originalTranslations: { ...session.review.originalTranslations },
    },
  };
}

/** A separate database keeps old segment-cache cleanup away from chapter drafts. */
export class IndexedDbDeltaSessionStore implements DeltaSessionRepository {
  private database: Promise<IDBPDatabase<DeltaDatabase>>;
  private pruning?: Promise<void>;
  constructor(name = 'gpt-delta-sessions') {
    this.database = openDB<DeltaDatabase>(name, DATABASE_VERSION, {
      upgrade(db) {
        db.createObjectStore('sessions', { keyPath: 'id' });
        db.createObjectStore('summaries', { keyPath: 'id' });
        db.createObjectStore('meta', { keyPath: 'key' });
      },
    });
  }

  async save(session: DeltaChapterSession): Promise<void> {
    const db = await this.database;
    const tx = db.transaction(['sessions', 'summaries'], 'readwrite');
    const sessions = tx.objectStore('sessions');
    const summaries = tx.objectStore('summaries');
    // Both stores are committed together; the small index owns the same revision.
    const previous = await summaries.get(session.id);
    if (previous && session.revision !== previous.revision + 1) {
      tx.abort();
      await tx.done.catch(() => {});
      throw new Error('Delta 断点版本冲突，已停止以保留已保存的回复');
    }
    const next = snapshot(session);
    pruneSessionHistory(next);
    const summary = summarizeDeltaSession(next);
    await sessions.put(next);
    await summaries.put(summary);
    await tx.done;
  }

  async get(id: string) {
    return (await this.database).get('sessions', id);
  }

  async listSummaries(): Promise<DeltaSessionSummary[]> {
    return (await this.database).getAll('summaries');
  }

  async delete(id: string) {
    const db = await this.database;
    const tx = db.transaction(['sessions', 'summaries'], 'readwrite');
    await tx.objectStore('sessions').delete(id);
    await tx.objectStore('summaries').delete(id);
    await tx.done;
  }

  async clear() {
    const db = await this.database;
    const tx = db.transaction(['sessions', 'summaries'], 'readwrite');
    await tx.objectStore('sessions').clear();
    await tx.objectStore('summaries').clear();
    await tx.done;
  }

  /**
   * Trim expired diagnostics at most once a day. Summaries stay untouched:
   * pruning only drops transcript/thinking payloads that a summary never
   * surfaces, so the display index does not need a rewrite.
   */
  async pruneThinking(): Promise<void> {
    if (!this.pruning) {
      this.pruning = this.pruneExpiredDiagnostics().finally(() => {
        this.pruning = undefined;
      });
    }
    return this.pruning;
  }

  private async pruneExpiredDiagnostics(): Promise<void> {
    const db = await this.database;
    const last = await db.get('meta', PRUNE_KEY);
    if (
      typeof last?.value === 'number' &&
      Date.now() - last.value < PRUNE_INTERVAL_MS
    )
      return;
    const tx = db.transaction(['sessions', 'meta'], 'readwrite');
    const sessions = tx.objectStore('sessions');
    const now = Date.now();
    let cursor = await sessions.openCursor();
    while (cursor) {
      const session = cursor.value;
      if (pruneSessionHistory(session, now)) await cursor.update(session);
      cursor = await cursor.continue();
    }
    await tx.objectStore('meta').put({ key: PRUNE_KEY, value: now });
    await tx.done;
  }
}
