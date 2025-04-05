import { Locator, formatError } from '@/data';
import { ChapterTranslation, LocalVolumeMetadata } from '@/model/LocalVolume';
import {
  LocalTranslateTaskDesc,
  TranslateTaskCallback,
  TranslateTaskParams,
} from '@/model/Translator';
import { Translator } from './Translator';

export const translateLocal = async (
  { volumeId }: LocalTranslateTaskDesc,
  { level, startIndex, endIndex }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translator: Translator,
  signal?: AbortSignal,
) => {
  const localVolumeRepository = await Locator.localVolumeRepository();
  // Api
  const getVolume = () => localVolumeRepository.getVolume(volumeId);

  const getChapter = (chapterId: string) =>
    localVolumeRepository.getChapter(volumeId, chapterId);

  const updateTranslation = (chapterId: string, json: ChapterTranslation) =>
    localVolumeRepository.updateTranslation(
      volumeId,
      chapterId,
      translator.id,
      json,
    );

  // Task
  let metadata: LocalVolumeMetadata;
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    const metadataOrUndefined = await getVolume();
    if (metadataOrUndefined === undefined) {
      throw '小说不存在';
    } else {
      metadata = metadataOrUndefined;
    }
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  const chapters = (() => {
    if (level === 'all') {
      return metadata.toc.slice(startIndex, endIndex).map((it) => it.chapterId);
    } else {
      const untranslatedChapters = metadata.toc
        .slice(startIndex, endIndex)
        .filter((it) => it[translator.id] === undefined)
        .map((it) => it.chapterId);
      if (level === 'normal') {
        return untranslatedChapters;
      }

      const expiredChapters = metadata.toc
        .slice(startIndex, endIndex)
        .filter(
          (it) =>
            it[translator.id] !== undefined &&
            it[translator.id] !== metadata.glossaryId,
        )
        .map((it) => it.chapterId);
      return untranslatedChapters.concat(expiredChapters);
    }
  })().sort((a, b) => a.localeCompare(b));

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  const forceSeg = level === 'all';

  // 对章节的并发翻译
  const chapterPromises = chapters.map(async (chapterId, index) => {
    try {
      // 检查是否已被取消
      if (signal?.aborted) {
        throw new Error('翻译任务已被取消');
      }

      callback.log(`\n[${index}] ${volumeId}/${chapterId}`);
      const chapter = await getChapter(chapterId);
      if (chapter === undefined) {
        throw new Error(`章节  ${index + 1} 不存在`); // 更改：打印章节序号
      }
      const textsJp = chapter?.paragraphs;

      const oldTextsZh = await localVolumeRepository.getChapter(
        volumeId,
        chapterId,
      );
      const textsZh = await translator.translate(textsJp, {
        chapterId: index + 1, // 新增：传递章节序号
        glossary: metadata.glossary,
        oldGlossary: chapter[translator.id]?.glossary,
        oldTextZh: oldTextsZh
          ? oldTextsZh[translator.id]?.paragraphs
          : undefined,
        force: forceSeg,
        signal, // 传递 AbortSignal
      });

      callback.log(`上传章节 ${index + 1}`);
      const state = await updateTranslation(chapterId, {
        glossaryId: metadata.glossaryId,
        glossary: metadata.glossary,
        paragraphs: textsZh,
      });
      callback.onChapterSuccess({ zh: state });
    } catch (e: any) {
      if (e.message === '翻译任务已被取消') {
        callback.log(`章节 ${index + 1} 被取消，停止翻译任务`);
        // 可以选择抛出错误以停止 Promise.allSettled
        throw e;
      } else if (e.name === 'AbortError') {
        callback.log(`中止章节 ${index + 1} 的翻译任务`); // 更改：打印章节序号
        // 同上，记录中止信息
      } else {
        callback.log(
          `章节 ${index + 1} 发生错误，跳过：${await formatError(e)}`,
        ); // 更改：打印章节序号
        callback.onChapterFailure();
      }
    }
  });

  // 等待所有章节的处理完成
  try {
    await Promise.allSettled(chapterPromises);
  } catch (e: any) {
    // 处理取消操作
    if (e instanceof Error && e.message === '翻译任务已被取消') {
      callback.log('翻译任务已被取消，所有并发操作已停止');
    } else if (e.name === 'AbortError') {
      callback.log('翻译任务中止，所有并发操作已停止');
    } else {
      throw e;
    }
  }
};
