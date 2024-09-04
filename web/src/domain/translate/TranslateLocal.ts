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
  for (const chapterId of chapters) {
    try {
      callback.log(`\n[${0}] ${volumeId}/${chapterId}`);
      const chapter = await getChapter(chapterId);
      if (chapter === undefined) {
        throw new Error('章节不存在');
      }
      const textsJp = chapter?.paragraphs;

      const oldTextsZh = await localVolumeRepository.getChapter(
        volumeId,
        chapterId,
      );
      const textsZh = await translator.translate(textsJp, {
        glossary: metadata.glossary,
        oldGlossary: chapter[translator.id]?.glossary,
        oldTextZh: oldTextsZh
          ? oldTextsZh[translator.id]?.paragraphs
          : undefined,
        force: forceSeg,
        signal,
      });

      callback.log('上传章节');
      const state = await updateTranslation(chapterId, {
        glossaryId: metadata.glossaryId,
        glossary: metadata.glossary,
        paragraphs: textsZh,
      });
      callback.onChapterSuccess({ zh: state });
    } catch (e: any) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else if (e.name === 'AbortError') {
        callback.log(`中止翻译任务`);
        return 'abort';
      } else {
        callback.log(`发生错误，跳过：${await formatError(e)}`);
        callback.onChapterFailure();
      }
    }
  }
};
