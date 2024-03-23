import { WebNovelRepository } from '@/data/api';
import { client } from '@/data/api/client';
import { LocalVolumeService } from '@/data/local';
import { ChapterTranslation, LocalVolumeMetadata } from '@/model/LocalVolume';
import { WebNovelDto } from '@/model/WebNovel';
import { delay, keepPageAlive } from '@/util';

import { Translator } from './translator';
import { SakuraTranslator } from './translator_sakura';

type WebTranslateTaskDesc = {
  type: 'web';
  providerId: string;
  novelId: string;
};

type WenkuTranslateTaskDesc = {
  type: 'wenku';
  novelId: string;
  volumeId: string;
};

type PersonalTranslateTaskDesc = {
  type: 'personal';
  volumeId: string;
};

export type TranslateTaskDesc =
  | WebTranslateTaskDesc
  | WenkuTranslateTaskDesc
  | PersonalTranslateTaskDesc;

export type TranslateTaskParams = {
  translateExpireChapter: boolean;
  overriteToc: boolean;
  syncFromProvider: boolean;
  startIndex: number;
  endIndex: number;
};

export type TranslateTaskCallback = {
  onStart: (total: number) => void;
  onChapterSuccess: (state: { jp?: number; zh?: number }) => void;
  onChapterFailure: () => void;
  log: (message: string, detail?: string[]) => void;
};

export type TranslatorDesc =
  | { id: 'baidu' }
  | { id: 'youdao' }
  | {
      id: 'gpt';
      type: 'web' | 'api';
      model: string;
      endpoint: string;
      key: string;
    }
  | { id: 'sakura'; endpoint: string; useLlamaApi: boolean };

const translateWeb = async (
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
  // Api
  const endpoint = `novel/${providerId}/${novelId}/translate/${translatorDesc.id}`;

  interface TranslateTaskDto {
    title?: string;
    introduction?: string;
    toc: string[];
    glossaryUuid?: string;
    glossary: { [key: string]: string };
    chapters: {
      id: string;
      state: 'untranslated' | 'translated' | 'expired';
    }[];
  }

  interface MetadataUpdateBody {
    title?: string;
    introduction?: string;
    toc: { [key: string]: string };
  }

  const getTranslateTask = () =>
    client.get(endpoint, { signal }).json<TranslateTaskDto>();

  const updateMetadataTranslation = (json: MetadataUpdateBody) =>
    client.post(`${endpoint}/metadata`, { json, signal }).text();

  const checkChapter = (chapterId: string) =>
    client
      .post(`${endpoint}/check-chapter/${chapterId}`, {
        searchParams: { sync: syncFromProvider },
        signal,
      })
      .json<string[]>();

  const updateChapterTranslation = (
    chapterId: string,
    json: {
      glossaryUuid?: string;
      paragraphsZh: string[];
    }
  ) =>
    client
      .put(`${endpoint}/chapter/${chapterId}`, {
        json: { ...json, sakuraVersion: '0.9' },
        signal,
      })
      .json<{ jp: number; zh: number }>();

  // Task
  let novel: WebNovelDto;
  let task: TranslateTaskDto;
  try {
    callback.log('获取元数据');
    novel = await WebNovelRepository.getNovel(providerId, novelId);
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
    const encodeMetadataToTranslate = () => {
      const query = [];
      if (!novel.titleZh) {
        query.push(novel.titleJp);
      }
      if (!novel.introductionZh) {
        query.push(novel.introductionJp);
      }
      const toc = novel.toc
        .filter((it) => overriteToc || !it.titleZh)
        .map((it) => it.titleJp);
      const tocWords = toc
        .map((it) => it.split(/[\s　]+/))
        .filter((it) => it.length > 0)
        .flat();
      query.push(...new Set(tocWords));
      return query;
    };

    const decodeAsMetadataTranslated = (translated: string[]) => {
      const obj: MetadataUpdateBody = { toc: {} };

      if (!novel.titleZh) {
        obj.title = translated.shift();
      }
      if (!novel.introductionZh) {
        obj.introduction = translated.shift();
      }
      const toc = novel.toc
        .filter((it) => overriteToc || !it.titleZh)
        .map((it) => it.titleJp);

      const tocWordsDict: { [key: string]: string } = {};
      const tocWords = toc
        .map((it) => it.split(/[\s　]+/))
        .filter((it) => it.length > 0)
        .flat();
      for (const textJp of [...new Set(tocWords)]) {
        tocWordsDict[textJp] = translated.shift()!!;
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

    if (overriteToc) {
      callback.log('重新翻译目录');
    }

    const textsSrc = encodeMetadataToTranslate();
    if (textsSrc.length > 0) {
      if (translatorDesc.id === 'gpt') {
        callback.log('目前GPT翻译目录超级不稳定，跳过');
      } else {
        callback.log('翻译元数据');
        const textsDst = await translator.translate(
          textsSrc,
          task.glossary,
          signal
        );

        callback.log(`上传元数据`);
        await updateMetadataTranslation(decodeAsMetadataTranslated(textsDst));
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
      callback.onChapterFailure();
    }
  }

  const chapters = task.chapters
    .map(({ id, state }, index) => ({ index, chapterId: id, state }))
    .slice(startIndex, endIndex)
    .filter(({ state }) => {
      if (state === 'untranslated') {
        return true;
      } else if (state === 'expired') {
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

const translateWenku = async (
  { novelId, volumeId }: WenkuTranslateTaskDesc,
  { translateExpireChapter }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc,
  signal?: AbortSignal
) => {
  // Api
  const endpoint = `wenku/${novelId}/translate/${translatorDesc.id}/${volumeId}`;

  interface TranslateTaskDto {
    glossaryUuid?: string;
    glossary: { [key: string]: string };
    untranslatedChapters: string[];
    expiredChapters: string;
  }
  const getTranslateTask = () =>
    client.get(endpoint, { signal }).json<TranslateTaskDto>();

  const getChapterToTranslate = (chapterId: string) =>
    client.get(`${endpoint}/${chapterId}`, { signal }).json<string[]>();

  const updateChapterTranslation = (
    chapterId: string,
    json: { glossaryUuid: string | undefined; paragraphsZh: string[] }
  ) =>
    client
      .put(`${endpoint}/${chapterId}`, {
        json: { ...json, sakuraVersion: '0.9' },
        signal,
      })
      .json<number>();

  // Task
  let task: TranslateTaskDto;
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

const translateLocal = async (
  { volumeId }: PersonalTranslateTaskDesc,
  { translateExpireChapter }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc,
  signal?: AbortSignal
) => {
  // Api
  const getVolume = () => LocalVolumeService.getVolume(volumeId);

  const getChapter = (chapterId: string) =>
    LocalVolumeService.getChapter(volumeId, chapterId);

  const updateTranslation = (chapterId: string, json: ChapterTranslation) =>
    LocalVolumeService.updateTranslation(
      volumeId,
      chapterId,
      translatorDesc.id,
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

  const untranslatedChapters = metadata.toc
    .filter((it) => it[translatorDesc.id] === undefined)
    .map((it) => it.chapterId);
  const expiredChapters = metadata.toc
    .filter(
      (it) =>
        it[translatorDesc.id] !== undefined &&
        it[translatorDesc.id] !== metadata.glossaryId
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
      callback.log(`\n获取章节 ${volumeId}/${chapterId}`);
      const chapter = await getChapter(chapterId);
      if (chapter === undefined) {
        throw new Error('章节不存在');
      }
      const textsJp = chapter?.paragraphs;

      callback.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsZh = await translator.translate(
        textsJp,
        metadata.glossary,
        signal
      );

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
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
        callback.log(`发生错误，跳过：${e}`);
        callback.onChapterFailure();
      }
    }
  }
};

export const translate = async (
  taskDesc: TranslateTaskDesc,
  taskParams: TranslateTaskParams,
  taskCallback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc,
  signal?: AbortSignal
) => {
  await keepPageAlive();

  if (taskDesc.type === 'web') {
    return translateWeb(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc,
      signal
    );
  } else if (taskDesc.type === 'wenku') {
    return translateWenku(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc,
      signal
    );
  } else {
    return translateLocal(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc,
      signal
    );
  }
};
