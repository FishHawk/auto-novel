import type {
  DeltaChapterSession,
  DeltaMetadata,
  DeltaSessionStore,
  DeltaChapterContext,
} from '@auto-novel/translator';
import type { ChapterDetail } from '../TranslationTask/types';
import type { ChapterMeta } from '../TaskState';
import type { DeltaSessionSummary } from './session-summary';

export interface DeltaSessionRepository extends DeltaSessionStore {
  get(id: string): Promise<DeltaChapterSession | undefined>;
  list(): Promise<DeltaChapterSession[]>;
  listSummaries(): Promise<DeltaSessionSummary[]>;
  delete(id: string): Promise<void>;
  clear(): Promise<void>;
  /** Prune expired diagnostic history before listing checkpoints. */
  pruneThinking?(): Promise<void>;
}

export interface DeltaTaskSource {
  novelId: string;
  volumeId?: string;
  metadata: DeltaMetadata;
  chapters: ChapterMeta[];
  readChapter(chapterId: string): Promise<ChapterDetail>;
  readChapterContext(
    chapterId: string,
    offset: number,
    signal?: AbortSignal,
  ): Promise<DeltaChapterContext>;
  uploadChapter(
    chapterId: string,
    glossaryId: string,
    paragraphs: string[],
  ): Promise<void>;
  /** Release any per-chapter task snapshot retained for upload. */
  disposeChapter?(chapterId: string): void;
}
