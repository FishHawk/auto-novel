import { WebNovelApi, formatError } from '@/api';
import type {
  TranslateTaskCallback,
  TranslateTaskParams,
  WebTranslateTask,
  WebTranslateTaskDesc,
} from '@/model/Translator';
import { delay } from '@/util';

import { CrawlerService } from '../crawler';
import type { Translator } from './Translator';
import { createWebMetadataCoder } from './WebMetadataCoder';

export const translateWeb = async (
  { providerId, novelId }: WebTranslateTaskDesc,
  {
    level,
    forceMetadata,
    useBrowserCrawler,
    startIndex,
    endIndex,
  }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translator: Translator,
  signal?: AbortSignal,
) => {
  if (useBrowserCrawler) {
    try {
      callback.log('获取小说信息');
      const novel = await WebNovelApi.getNovel(providerId, novelId);
      callback.log('通过浏览器爬虫更新目录');
      await CrawlerService.updateWebNovel(providerId, novelId, novel, {
        safeUpdateToc: level !== 'sync',
      });
      callback.log('目录已更新');
    } catch (e) {
      if (e instanceof DOMException && e.name === 'AbortError') {
        callback.log(`中止翻译任务`);
        return 'abort';
      } else {
        callback.log(`目录更新跳过：${await formatError(e)}`);
      }
    }
  }

  const {
    getTranslateTask,
    getChapterTranslateTask,
    updateMetadataTranslation,
    updateChapterTranslation,
  } = WebNovelApi.createTranslationApi(
    providerId,
    novelId,
    translator.id,
    level === 'sync',
    signal,
  );

  // Task
  let task: WebTranslateTask;
  try {
    callback.log('获取翻译任务');
    // 临时手段解决timeout，等数据库大修完成后删去
    try {
      task = await getTranslateTask();
    } catch (e: unknown) {
      callback.log('获取翻译任务-延迟10s重试');
      await delay(10_000, signal);
      task = await getTranslateTask();
    }
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      callback.log(`中止翻译任务`);
      return 'abort';
    } else {
      callback.log(`发生错误，结束翻译任务：${e}`);
      return;
    }
  }

  try {
    const coder = createWebMetadataCoder(task, forceMetadata);

    if (forceMetadata) {
      callback.log('重新翻译目录');
    }

    if (coder.needUpload) {
      callback.log('翻译元数据');
      const textsDst = await translator.translate(coder.encoded, {
        glossary: task.glossary,
        signal,
      });

      callback.log(`上传元数据`);
      await updateMetadataTranslation(coder.recover(textsDst));
    }
  } catch (e) {
    if (e === 'quit') {
      callback.log(`发生错误，结束翻译任务`);
      return;
    } else if (e instanceof DOMException && e.name === 'AbortError') {
      callback.log(`中止翻译任务`);
      return 'abort';
    } else {
      callback.log(`发生错误，跳过：${await formatError(e)}`);
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
      if (level === 'normal') {
        return glossaryUuid === undefined;
      } else if (level === 'expire') {
        return glossaryUuid === undefined || glossaryUuid !== task.glossaryUuid;
      } else {
        return true;
      }
    });

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  const forceSeg = level === 'all';
  for (const { index, chapterId } of chapters) {
    try {
      callback.log(`\n[${index}] ${providerId}/${novelId}/${chapterId}`);
      let shouldSyncFromProvider = level === 'sync';
      if (useBrowserCrawler) {
        callback.log(`通过浏览器爬虫更新章节`);
        try {
          const action = await CrawlerService.updateWebNovelChapter(
            providerId,
            novelId,
            chapterId,
            level === 'sync',
          );
          callback.log(action === 'created' ? '章节已创建' : '章节已更新');
          shouldSyncFromProvider = false;
        } catch (e) {
          callback.log(`章节更新跳过：${await formatError(e)}`);
        }
      }
      const cTask = await getChapterTranslateTask(chapterId, {
        syncFromProvider: shouldSyncFromProvider,
      });

      if (!forceSeg && cTask.glossaryId === cTask.oldGlossaryId) {
        callback.log(`无需翻译`);

        callback.onChapterSuccess({});
      } else {
        const textsZh = await translator.translate(cTask.paragraphJp, {
          glossary: cTask.glossary,
          oldTextZh: cTask.oldParagraphZh,
          oldGlossary: cTask.oldGlossary,
          force: forceSeg,
          signal,
        });
        callback.log(`上传章节`);
        const { jp, zh } = await updateChapterTranslation(chapterId, {
          glossaryId: cTask.glossaryId,
          paragraphsZh: textsZh,
        });
        callback.onChapterSuccess({ jp, zh });
      }
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else if (e instanceof DOMException && e.name === 'AbortError') {
        callback.log(`中止翻译任务`);
        return 'abort';
      } else {
        callback.log(`发生错误，跳过：${await formatError(e)}`);
        callback.onChapterFailure();
      }
    }
  }
};
