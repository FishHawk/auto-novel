import { Locator, formatError } from '@/data';
import { ChapterTranslation, LocalVolumeMetadata } from '@/model/LocalVolume';
import {
  PersonalTranslateTaskDesc,
  TranslateTaskCallback,
  TranslateTaskParams,
} from '@/model/Translator';

import { Translator } from './Translator';

export const translateLocal = async (
  { volumeId }: PersonalTranslateTaskDesc,
  { translateExpireChapter }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translator: Translator,
  signal?: AbortSignal
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
      json
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

  const untranslatedChapters = metadata.toc
    .filter((it) => it[translator.id] === undefined)
    .map((it) => it.chapterId);
  const expiredChapters = metadata.toc
    .filter(
      (it) =>
        it[translator.id] !== undefined &&
        it[translator.id] !== metadata.glossaryId
    )
    .map((it) => it.chapterId);
  const chapters = (
    translateExpireChapter
      ? untranslatedChapters.concat(expiredChapters)
      : untranslatedChapters
  ).sort((a, b) => a.localeCompare(b));

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const chapterId of chapters) {
    try {
      callback.log(`\n[${0}] ${volumeId}/${chapterId}`);
      const chapter = await getChapter(chapterId);
      if (chapter === undefined) {
        throw new Error('章节不存在');
      }
      const textsJp = chapter?.paragraphs;

      const textsZh = await translator.translate(textsJp, {
        glossary: metadata.glossary,
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
