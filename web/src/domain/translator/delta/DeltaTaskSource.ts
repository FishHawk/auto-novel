import { TranslateTaskDescriptor } from '@/model/Translator';
import { WebNovelApi, WenkuNovelApi } from '@/api';
import { useLocalVolumeStore } from '@/stores';
import { createTranslationTask } from '../TranslationTask/createTranslationTask';
import type { DeltaTaskSource } from './types';
import type { TranslationTask } from '../TranslationTask/types';
import type { DeltaChapterContext } from '@auto-novel/translator';

type ContextChapter = { chapterId: string; title: string };
const CONTEXT_MAX_CHARS = 40000;

function boundedContext(
  chapter: ContextChapter,
  source: string[],
  translation?: string[] | null,
): DeltaChapterContext {
  const size = source.join('\n').length + (translation?.join('\n').length ?? 0);
  if (size > CONTEXT_MAX_CHARS)
    return {
      available: false,
      reason: '相邻章节上下文超过 40000 字符，本次未返回文本',
    };
  return {
    available: true,
    ...chapter,
    source,
    ...(translation ? { translation } : {}),
  };
}

/** Delta reuses data access only; no legacy executor or segment queue is involved. */
export async function createDeltaTaskSource(
  taskDesc: string,
): Promise<DeltaTaskSource> {
  const { desc, params } = TranslateTaskDescriptor.parse(taskDesc);
  const task = createTranslationTask(desc, 'gpt', params);
  const chapterTasks = new Map<string, TranslationTask>();
  await task.initMeta();
  const metadata = {
    title: task.description,
    description: '',
    sourceLanguage: '日语',
    targetLanguage: '简体中文',
  };
  let directory: ContextChapter[] | undefined;
  if (desc.type === 'web') {
    const novel = await WebNovelApi.getNovel(desc.providerId, desc.novelId);
    metadata.title = novel.titleJp;
    metadata.description = novel.introductionJp;
    directory = (novel.toc ?? []).flatMap((item) =>
      item.chapterId == null
        ? []
        : [{ chapterId: item.chapterId, title: item.titleJp }],
    );
  } else if (desc.type === 'wenku') {
    const novel = await WenkuNovelApi.getNovel(desc.novelId);
    metadata.title = novel.title;
    metadata.description = novel.introduction;
  } else if (desc.type === 'local') metadata.title = desc.volumeId;
  return {
    novelId: JSON.stringify(desc),
    volumeId:
      desc.type === 'local' || desc.type === 'wenku'
        ? desc.volumeId
        : undefined,
    metadata,
    chapters: task.chapters,
    readChapter: async (chapterId) => {
      // Fresh glossary is necessary for local uploads too: LocalTranslationTask
      // otherwise keeps the glossary captured by its first initMeta call.
      const chapterTask = createTranslationTask(desc, 'gpt', params);
      // Web reads and uploads do not depend on metadata; a per-chapter
      // initMeta would fetch the complete TOC for every translated chapter.
      if (desc.type !== 'web') await chapterTask.initMeta();
      chapterTasks.set(chapterId, chapterTask);
      const detail = await chapterTask.fetchChapter(chapterId);
      // Only the glossary/api snapshot is needed after the read.
      chapterTask.releaseMeta?.();
      return detail;
    },
    readChapterContext: async (chapterId, offset, signal) => {
      signal?.throwIfAborted();
      if (!Number.isInteger(offset) || offset === 0 || Math.abs(offset) > 2)
        return {
          available: false,
          reason: '仅允许读取前后 2 章（offset 为 ±1 或 ±2）',
        };
      try {
        if (!directory) {
          if (desc.type === 'wenku') {
            const fullTask = await WenkuNovelApi.createTranslationApi(
              desc.novelId,
              desc.volumeId,
              'gpt',
              signal,
            ).getTranslateTask();
            directory = fullTask.toc.map(({ chapterId }) => ({
              chapterId,
              title: chapterId,
            }));
          } else if (desc.type === 'local') {
            const store = await useLocalVolumeStore();
            const volume = await store.getVolume(desc.volumeId);
            if (!volume) return { available: false, reason: '本地卷不存在' };
            directory = volume.toc.map(({ chapterId }) => ({
              chapterId,
              title: chapterId,
            }));
          }
        }
        if (!directory) return { available: false, reason: '完整目录不可用' };
        signal?.throwIfAborted();
        const index = directory.findIndex(
          (chapter) => chapter.chapterId === chapterId,
        );
        const target = index < 0 ? undefined : directory[index + offset];
        if (!target)
          return {
            available: false,
            reason: '相邻章节不在本小说或卷的完整目录中',
          };
        let context: DeltaChapterContext;
        if (desc.type === 'web') {
          // Reuse the existing read API with forced sync disabled. The server may
          // still fetch a missing chapter; this tool never submits a translation.
          const chapter = await WebNovelApi.createTranslationApi(
            desc.providerId,
            desc.novelId,
            'gpt',
            false,
            signal,
          ).getChapterTranslateTask(target.chapterId);
          context = boundedContext(
            target,
            chapter.paragraphJp,
            chapter.oldParagraphZh,
          );
        } else if (desc.type === 'wenku') {
          const chapter = await WenkuNovelApi.createTranslationApi(
            desc.novelId,
            desc.volumeId,
            'gpt',
            signal,
          ).getChapterTranslateTask(target.chapterId);
          context = boundedContext(
            target,
            chapter.paragraphJp,
            chapter.oldParagraphZh,
          );
        } else if (desc.type === 'local') {
          const store = await useLocalVolumeStore();
          const chapter = await store.getChapter(
            desc.volumeId,
            target.chapterId,
          );
          context = chapter
            ? boundedContext(
                target,
                chapter.paragraphs,
                chapter.gpt?.paragraphs,
              )
            : { available: false, reason: '本地章节尚未保存' };
        } else return { available: false, reason: '任务上下文不可用' };
        signal?.throwIfAborted();
        return context;
      } catch (error) {
        signal?.throwIfAborted();
        if (error instanceof DOMException && error.name === 'AbortError')
          throw error;
        return {
          available: false,
          reason: `站内章节上下文不可用：${String(error)}`,
        };
      }
    },
    uploadChapter: async (chapterId, glossaryId, paragraphs) => {
      const chapterTask = chapterTasks.get(chapterId);
      if (!chapterTask) throw new Error('章节上传上下文已释放，请重新翻译该章');
      await chapterTask.uploadChapter(chapterId, glossaryId, paragraphs);
      chapterTasks.delete(chapterId);
    },
    disposeChapter: (chapterId) => {
      chapterTasks.delete(chapterId);
    },
  };
}
