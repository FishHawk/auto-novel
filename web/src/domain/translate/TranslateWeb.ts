import { Locator } from '@/data';
import {
  TranslateTaskCallback,
  TranslateTaskParams,
  TranslatorDesc,
  WebTranslateTask,
  WebTranslateTaskDesc,
} from '@/model/Translator';
import { delay } from '@/util';

import { Translator } from './Translator';
import { SakuraTranslator } from './TranslatorSakura';

export const translateWeb = async (
  { providerId, novelId }: WebTranslateTaskDesc,
  {
    translateExpireChapter,
    overriteToc,
    syncFromProvider,
    startIndex,
    endIndex,
  }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc,
  signal?: AbortSignal
) => {
  const {
    getTranslateTask,
    updateMetadataTranslation,
    checkChapter,
    updateChapterTranslation,
  } = Locator.webNovelRepository.createTranslationApi(
    providerId,
    novelId,
    translatorDesc.id,
    syncFromProvider,
    signal
  );

  // Task
  let task: WebTranslateTask;
  try {
    callback.log('获取翻译任务');
    // 临时手段解决timeout，等数据库大修完成后删去
    try {
      task = await getTranslateTask();
    } catch (e: any) {
      callback.log('获取翻译任务-延迟10s重试');
      await delay(10_000, signal);
      task = await getTranslateTask();
    }
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

  try {
    const createMetadataCoder = () => {
      const encoded: string[] = [];
      if (!task.titleZh) {
        encoded.push(task.titleJp);
      }
      if (!task.introductionZh) {
        encoded.push(task.introductionJp);
      }
      const toc = task.toc
        .filter((it) => overriteToc || !it.titleZh)
        .map((it) => it.titleJp);

      const tocWordsDict: { [key: string]: string } = {};
      const tocWords = Array.from(
        new Set(
          toc
            .map((it) => it.split(/[\s　]+/))
            .filter((it) => it.length > 0)
            .flat()
        )
      );

      const preTranslateTocWord = (wordJp: string) => {
        if (/^第?[０-９0-9]+話?$/.test(wordJp)) {
          return wordJp.replace('話', '话');
        } else if (wordJp === '閑話') {
          return '闲话';
        } else if (wordJp === '幕間') {
          return '幕间';
        }
      };
      const tocWordsNeedTranslate = tocWords.filter((wordJp) => {
        const wordZh = preTranslateTocWord(wordJp);
        if (wordZh !== undefined) {
          tocWordsDict[wordJp] = wordZh;
          return false;
        } else {
          return true;
        }
      });
      encoded.push(...tocWordsNeedTranslate);

      const recover = (translated: string[]) => {
        const obj: {
          title?: string;
          introduction?: string;
          toc: { [key: string]: string };
        } = { toc: {} };

        if (!task.titleZh) {
          obj.title = translated.shift();
        }
        if (!task.introductionZh) {
          obj.introduction = translated.shift();
        }
        for (const wordJp of tocWordsNeedTranslate) {
          tocWordsDict[wordJp] = translated.shift()!!;
        }

        new Set(toc).forEach((it) => {
          const spaces = it.split(/[^\s　]+/).filter((it) => it.length > 0);
          obj.toc[it] = it
            .split(/[\s　]+/)
            .map((it) => {
              if (it.length === 0) {
                return [spaces.shift() ?? ''];
              } else {
                return [tocWordsDict[it], spaces.shift() ?? ''];
              }
            })
            .flat()
            .join('');
        });
        return obj;
      };

      return {
        encoded,
        needUpload: encoded.length > 0 || toc.length > 0,
        recover,
      };
    };

    const coder = createMetadataCoder();

    if (overriteToc) {
      callback.log('重新翻译目录');
    }

    if (coder.needUpload) {
      if (translatorDesc.id === 'gpt') {
        callback.log('目前GPT翻译目录超级不稳定，跳过');
      } else {
        callback.log('翻译元数据');
        const textsDst = await translator.translate(
          coder.encoded,
          task.glossary,
          signal
        );

        callback.log(`上传元数据`);
        await updateMetadataTranslation(coder.recover(textsDst));
      }
    }
  } catch (e: any) {
    if (e === 'quit') {
      callback.log(`发生错误，结束翻译任务`);
      return;
    } else if (e.name === 'AbortError') {
      callback.log(`中止翻译任务`);
      return 'abort';
    } else {
      callback.log(`发生错误，跳过：${e}`);
    }
  }

  const chapters = task.toc
    .filter(({ chapterId }) => chapterId !== undefined)
    .map(({ chapterId, glossaryUuid }, index) => ({
      index,
      chapterId: chapterId!,
      glossaryUuid,
    }))
    .slice(startIndex, endIndex)
    .filter(({ glossaryUuid }) => {
      if (glossaryUuid === undefined) {
        return true;
      } else if (glossaryUuid !== task.glossaryUuid) {
        return translateExpireChapter || syncFromProvider;
      } else {
        return syncFromProvider;
      }
    });

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const { index, chapterId } of chapters) {
    if (index < startIndex || index >= endIndex) {
      continue;
    }

    const logSuffix = `[${index}] ${providerId}/${novelId}/${chapterId}`;
    try {
      callback.log('\n获取章节' + logSuffix);
      const textsJp = await checkChapter(chapterId);

      if (textsJp.length === 0) {
        callback.log(`无需翻译`);
        callback.onChapterSuccess({});
      } else {
        callback.log('翻译章节' + logSuffix);
        const textsZh = await translator.translate(
          textsJp,
          task.glossary,
          signal
        );
        callback.log('上传章节' + logSuffix);
        const { jp, zh } = await updateChapterTranslation(chapterId, {
          glossaryUuid: task.glossaryUuid,
          paragraphsZh: textsZh,
        });
        callback.onChapterSuccess({ jp, zh });
      }
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
