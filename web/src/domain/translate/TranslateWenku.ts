import { Locator } from '@/data';
import {
  TranslateTaskCallback,
  TranslateTaskParams,
  TranslatorDesc,
  WenkuTranslateTask,
  WenkuTranslateTaskDesc,
} from '@/model/Translator';

import { Translator } from './Translator';
import { SakuraTranslator } from './TranslatorSakura';

export const translateWenku = async (
  { novelId, volumeId }: WenkuTranslateTaskDesc,
  { translateExpireChapter }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc,
  signal?: AbortSignal
) => {
  const { getTranslateTask, getChapterToTranslate, updateChapterTranslation } =
    Locator.wenkuNovelRepository.createTranslationApi(
      novelId,
      volumeId,
      translatorDesc.id,
      signal
    );

  // Task
  let task: WenkuTranslateTask;
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    task = await getTranslateTask();
  } catch (e: any) {
    if (e.name === 'AbortError') {
      callback.log(`中止翻译任务`);
      return 'abort';
    } else {
      callback.log(`发生错误，结束翻译任务：${e}`);
      return;
    }
  }

  let translator: Translator;
  try {
    translator = await Translator.create(
      {
        log: (message, detail) => callback.log('　　' + message, detail),
        ...translatorDesc,
      },
      true
    );
  } catch (e: any) {
    callback.log(`发生错误，无法创建翻译器：${e}`);
    return;
  }

  if (
    translator.segTranslator instanceof SakuraTranslator &&
    !translator.segTranslator.allowUpload()
  ) {
    callback.log('发生错误，当前Sakura版本不允许上传翻译');
    return;
  }

  const chapters = (
    translateExpireChapter
      ? task.untranslatedChapters.concat(task.expiredChapters)
      : task.untranslatedChapters
  ).sort((a, b) => a.localeCompare(b));

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const chapterId of chapters) {
    try {
      callback.log(`\n获取章节 ${volumeId}/${chapterId}`);
      const textsJp = await getChapterToTranslate(chapterId);

      callback.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsZh = await translator.translate(
        textsJp,
        task.glossary,
        signal
      );

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await updateChapterTranslation(chapterId, {
        glossaryUuid: task.glossaryUuid,
        paragraphsZh: textsZh,
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
        callback.log(`发生错误，跳过：${e}`);
        callback.onChapterFailure();
      }
    }
  }
};
