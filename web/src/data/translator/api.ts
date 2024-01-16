import { client } from '@/data/api/client';

import { ChapterTranslation, PersonalVolumesManager } from './db/personal';
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
      model: 'gpt-3.5' | 'gpt-4';
      endpoint: string;
      key: string;
    }
  | { id: 'sakura'; endpoint: string; useLlamaApi: boolean };

const translateWeb = async (
  { providerId, novelId }: WebTranslateTaskDesc,
  {
    syncFromProvider,
    //
    startIndex,
    endIndex,
    translateExpireChapter,
  }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc
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

  const getTranslateTask = () => client.get(endpoint).json<TranslateTaskDto>();

  const updateMetadataTranslation = (json: MetadataUpdateBody) =>
    client.post(`${endpoint}/metadata`, { json }).text();

  const checkChapter = (chapterId: string) =>
    client
      .post(`${endpoint}/check-chapter/${chapterId}`, {
        searchParams: { sync: syncFromProvider },
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
      .put(`${endpoint}/chapter/${chapterId}`, { json })
      .json<{ jp: number; zh: number }>();

  const encodeMetadataToTranslate = (metadata: TranslateTaskDto) => {
    const query = [];
    if (metadata.title) {
      query.push(metadata.title);
    }
    if (metadata.introduction) {
      query.push(metadata.introduction);
    }
    query.push(...metadata.toc);
    return query;
  };

  const decodeAsMetadataTranslated = (
    metadata: TranslateTaskDto,
    translated: string[]
  ) => {
    const obj: MetadataUpdateBody = { toc: {} };
    if (metadata.title) {
      obj.title = translated.shift();
    }
    if (metadata.introduction) {
      obj.introduction = translated.shift();
    }
    for (const textJp of metadata.toc) {
      obj.toc[textJp] = translated.shift()!!;
    }
    return obj;
  };

  // Task
  let task: TranslateTaskDto;
  try {
    callback.log('获取元数据');
    task = await getTranslateTask();
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  let translator: Translator;
  try {
    translator = await Translator.create({
      client,
      glossary: task.glossary,
      log: (message, detail) => callback.log('　　' + message, detail),
      ...translatorDesc,
    });
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
    const textsSrc = encodeMetadataToTranslate(task);
    if (textsSrc.length > 0) {
      if (translatorDesc.id === 'gpt') {
        callback.log('目前GPT翻译目录超级不稳定，跳过');
      } else {
        callback.log('翻译元数据');
        const textsDst = await translator.translate(textsSrc);

        callback.log(`上传元数据`);
        await updateMetadataTranslation(
          decodeAsMetadataTranslated(task, textsDst)
        );
      }
    }
  } catch (e: any) {
    if (e === 'quit') {
      callback.log(`发生错误，结束翻译任务`);
      return;
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
        const textsZh = await translator.translate(textsJp);
        callback.log('上传章节' + logSuffix);
        const { jp, zh } = await updateChapterTranslation(chapterId, {
          glossaryUuid: task.glossaryUuid,
          paragraphsZh: textsZh,
        });
        callback.onChapterSuccess({ jp, zh });
      }
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
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
  translatorDesc: TranslatorDesc
) => {
  // Api
  const endpoint = `wenku/${novelId}/translate/${translatorDesc.id}/${volumeId}`;

  interface TranslateTaskDto {
    glossaryUuid?: string;
    glossary: { [key: string]: string };
    untranslatedChapters: string[];
    expiredChapters: string;
  }
  const getTranslateTask = () => client.get(endpoint).json<TranslateTaskDto>();

  const getChapterToTranslate = (chapterId: string) =>
    client.get(`${endpoint}/${chapterId}`).json<string[]>();

  const updateChapterTranslation = (
    chapterId: string,
    json: { glossaryUuid: string | undefined; paragraphsZh: string[] }
  ) => client.put(`${endpoint}/${chapterId}`, { json }).json<number>();

  // Task
  let task: TranslateTaskDto;
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    task = await getTranslateTask();
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  let translator: Translator;
  try {
    translator = await Translator.create({
      client,
      glossary: task.glossary,
      log: (message) => callback.log('　　' + message),
      ...translatorDesc,
    });
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
      const textsZh = await translator.translate(textsJp);

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await updateChapterTranslation(chapterId, {
        glossaryUuid: task.glossaryUuid,
        paragraphsZh: textsZh,
      });
      callback.onChapterSuccess({ zh: state });
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else {
        callback.log(`发生错误，跳过：${e}`);
        callback.onChapterFailure();
      }
    }
  }
};

const translatePersonal = async (
  { volumeId }: PersonalTranslateTaskDesc,
  { translateExpireChapter }: TranslateTaskParams,
  callback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc
) => {
  // Api

  const getTranslateTask = () =>
    PersonalVolumesManager.getTranslateTask(volumeId, translatorDesc.id);

  const getChapterToTranslate = (chapterId: string) =>
    PersonalVolumesManager.getChapterToTranslate(volumeId, chapterId);

  const updateChapterTranslation = (
    chapterId: string,
    json: ChapterTranslation
  ) =>
    PersonalVolumesManager.updateChapterTranslation(
      volumeId,
      chapterId,
      translatorDesc.id,
      json
    );

  // Task
  let task: Awaited<ReturnType<typeof getTranslateTask>>;
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    task = await getTranslateTask();
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  let translator: Translator;
  try {
    translator = await Translator.create({
      client,
      glossary: task.glossary,
      log: (message) => callback.log('　　' + message),
      ...translatorDesc,
    });
  } catch (e: any) {
    callback.log(`发生错误，无法创建翻译器：${e}`);
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
      const textsZh = await translator.translate(textsJp);

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await updateChapterTranslation(chapterId, {
        glossaryId: task.glossaryUuid,
        glossary: task.glossary,
        paragraphs: textsZh,
      });
      callback.onChapterSuccess({ zh: state });
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else {
        callback.log(`发生错误，跳过：${e}`);
        callback.onChapterFailure();
      }
    }
  }
};

export const translate = (
  taskDesc: TranslateTaskDesc,
  taskParams: TranslateTaskParams,
  taskCallback: TranslateTaskCallback,
  translatorDesc: TranslatorDesc
) => {
  if (taskDesc.type === 'web') {
    return translateWeb(taskDesc, taskParams, taskCallback, translatorDesc);
  } else if (taskDesc.type === 'wenku') {
    return translateWenku(taskDesc, taskParams, taskCallback, translatorDesc);
  } else {
    return translatePersonal(
      taskDesc,
      taskParams,
      taskCallback,
      translatorDesc
    );
  }
};
