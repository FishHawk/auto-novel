/* eslint-disable compat/compat -- structuredClone is feature-detected with a lodash fallback. */
import { openDB, type DBSchema, type IDBPDatabase } from 'idb';
import { cloneDeep } from 'lodash-es';
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

/** Native cloning avoids lodash's slow object graph walk on large transcripts. */
function snapshot<T>(value: T): T {
  return typeof structuredClone === 'function'
    ? structuredClone(value)
    : cloneDeep(value);
}

/** A separate database keeps old segment-cache cleanup away from chapter drafts. */
export class IndexedDbDeltaSessionStore implements DeltaSessionRepository {
  private database: Promise<IDBPDatabase<DeltaDatabase>>;
  private pruning?: Promise<void>;
  constructor(name = 'gpt-delta-sessions') {
    this.database = openDB<DeltaDatabase>(name, DATABASE_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('sessions'))
          db.createObjectStore('sessions', { keyPath: 'id' });
        if (!db.objectStoreNames.contains('summaries'))
          db.createObjectStore('summaries', { keyPath: 'id' });
        if (!db.objectStoreNames.contains('meta'))
          db.createObjectStore('meta', { keyPath: 'key' });
      },
    });
  }

  async save(session: DeltaChapterSession): Promise<void> {
    const db = await this.database;
    const tx = db.transaction(['sessions', 'summaries'], 'readwrite');
    const sessions = tx.objectStore('sessions');
    const previous = await sessions.get(session.id);
    if (previous && session.revision !== previous.revision + 1) {
      tx.abort();
      await tx.done.catch(() => {});
      throw new Error('Delta 断点版本冲突，已停止以保留已保存的回复');
    }
    // IndexedDB performs another structured clone on put; the explicit one is
    // the single snapshot the caller is allowed to retain and mutate.
    const next = snapshot(session);
    pruneSessionHistory(next);
    await sessions.put(next);
    await tx.objectStore('summaries').put(summarizeDeltaSession(next));
    await tx.done;
  }

  async get(id: string) {
    return (await this.database).get('sessions', id);
  }

  async list() {
    return (await this.database).getAll('sessions');
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
      try {
        if (pruneSessionHistory(session, now)) await cursor.update(session);
      } catch (error) {
        console.warn('Delta 历史清理跳过损坏检查点', session?.id, error);
      }
      cursor = await cursor.continue();
    }
    await tx.objectStore('meta').put({ key: PRUNE_KEY, value: now });
    await tx.done;
  }

  async close() {
    (await this.database).close();
  }
}
