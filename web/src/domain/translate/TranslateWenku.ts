import { Locator, formatError } from '@/data';
import {
  TranslateTaskCallback,
  TranslateTaskParams,
  WenkuTranslateTask,
  WenkuTranslateTaskDesc,
} from '@/model/Translator';

import { Translator } from './Translator';

export const translateWenku = async (
  { novelId, volumeId }: WenkuTranslateTaskDesc,
  { translateExpireChapter }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translator: Translator,
  signal?: AbortSignal
) => {
  const {
    getTranslateTask,
    getChapterTranslateTask,
    updateChapterTranslation,
  } = Locator.wenkuNovelRepository.createTranslationApi(
    novelId,
    volumeId,
    translator.id,
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

  const chapters = task.toc
    .filter(({ chapterId }) => chapterId !== undefined)
    .map(({ chapterId, glossaryId }, index) => ({
      index,
      chapterId: chapterId!,
      glossaryId,
    }))
    .filter(({ glossaryId }) => {
      if (glossaryId === undefined) {
        return true;
      } else if (glossaryId !== task.glossaryId) {
        return translateExpireChapter;
      } else {
        return false;
      }
    });

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const { index, chapterId } of chapters) {
    try {
      callback.log(`\n[${index}] ${volumeId}/${chapterId}`);
      const chapterTranslateTask = await getChapterTranslateTask(chapterId);

      if (chapterTranslateTask === '') {
        callback.log(`无需翻译`);
        callback.onChapterSuccess({});
      } else {
        const textsZh = await translator.translate(
          chapterTranslateTask.paragraphJp,
          {
            glossary: chapterTranslateTask.glossary,
            oldTextZh: chapterTranslateTask.oldParagraphZh,
            oldGlossary: chapterTranslateTask.oldGlossary,
            signal,
          }
        );
        callback.log('上传章节');
        const state = await updateChapterTranslation(chapterId, {
          glossaryId: chapterTranslateTask.glossaryId,
          paragraphsZh: textsZh,
        });
        callback.onChapterSuccess({ zh: state });
      }
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
