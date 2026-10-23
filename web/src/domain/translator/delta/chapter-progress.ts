import type { DeltaChapterSession } from '@auto-novel/translator';
import type { ChapterMeta } from '../TaskState';
import type { DeltaSessionSummary } from './session-summary';

type ChapterProgressSession =
  | Pick<
      DeltaSessionSummary,
      | 'identity'
      | 'metadata'
      | 'phase'
      | 'pauseReason'
      | 'cursor'
      | 'batchCount'
    >
  | Pick<
      DeltaChapterSession,
      'identity' | 'metadata' | 'phase' | 'pauseReason' | 'cursor' | 'batches'
    >;

/** Local checkpoints remain visible when normal task filtering omits already uploaded chapters. */
export function syncDeltaChapterProgress(
  chapters: ChapterMeta[],
  session: ChapterProgressSession,
): ChapterMeta[] {
  const batchCount =
    'batchCount' in session ? session.batchCount : session.batches.length;
  const index = chapters.findIndex(
    (chapter) => chapter.chapterId === session.identity.chapterId,
  );
  const chapter: ChapterMeta = {
    ...(index < 0
      ? {
          chapterId: session.identity.chapterId,
          title:
            session.identity.title ??
            session.metadata.chapterTitle ??
            session.identity.chapterId,
          order: chapters.length,
        }
      : chapters[index]),
    status:
      session.phase === 'completed'
        ? 'done'
        : session.pauseReason
          ? session.pauseReason.includes('停止')
            ? 'pending'
            : 'paused'
          : 'translating',
    segmentProgress: {
      completed: session.phase === 'completed' ? batchCount : session.cursor,
      total: batchCount,
    },
  };
  return index < 0
    ? [...chapters, chapter]
    : chapters.map((item, i) => (i === index ? chapter : item));
}
