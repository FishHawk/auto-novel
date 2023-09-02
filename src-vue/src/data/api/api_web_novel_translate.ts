import {
  TranslatorConfig,
  TranslatorId,
  createTranslator,
} from '@/data/translator/translator';
import { Translator } from '@/data/translator/base';

import { api } from './api';

// Api

interface TranslateTaskDto {
  title?: string;
  introduction?: string;
  toc: string[];
  glossaryUuid?: string;
  glossary: { [key: string]: string };
  untranslatedChapters: { [key: number]: string };
  expiredChapters: { [key: number]: string };
}

const getTranslateTask = (
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  startIndex: number,
  endIndex: number
) =>
  api
    .get(`novel/${providerId}/${novelId}/translate/${translatorId}`, {
      searchParams: { startIndex, endIndex },
    })
    .json<TranslateTaskDto>();

interface MetadataUpdateBody {
  title?: string;
  introduction?: string;
  toc: { [key: string]: string };
}

const postMetadata = (
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  body: MetadataUpdateBody
) =>
  api
    .post(`novel/${providerId}/${novelId}/translate/${translatorId}/metadata`, {
      json: body,
    })
    .text();

interface ChapterToTranslateDto {
  glossary: { [key: string]: string };
  paragraphsJp: string[];
}

interface TranslateStateDto {
  jp: number;
  zh: number;
}

const getChapter = (
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  chapterId: string
) =>
  api
    .get(
      `novel/${providerId}/${novelId}/translate/${translatorId}/chapter/${chapterId}`
    )
    .json<ChapterToTranslateDto>();

interface ChapterUpdateBody {
  glossaryUuid: string | undefined;
  paragraphsZh: string[];
}

const postChapter = (
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  chapterId: string,
  body: ChapterUpdateBody
) =>
  api
    .post(
      `novel/${providerId}/${novelId}/translate/${translatorId}/chapter/${chapterId}`,
      { json: body }
    )
    .json<TranslateStateDto>();

interface ChapterUpdatePartlyBody {
  glossaryUuid: string | undefined;
  paragraphsZh: { [key: number]: string };
}

const putChapter = (
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  chapterId: string,
  body: ChapterUpdatePartlyBody
) =>
  api
    .patch(
      `novel/${providerId}/${novelId}/translate/${translatorId}/chapter/${chapterId}`,
      { json: body }
    )
    .json<TranslateStateDto>();

// Translate

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

const getExpiredParagraphs = (
  chapter: ChapterToTranslateDto,
  glossary: { [key: string]: string }
) => {
  const changedWords: string[] = [];
  for (const word in glossary) {
    if (chapter.glossary[word] != glossary[word]) {
      changedWords.push(word);
    }
  }
  for (const word in chapter.glossary) {
    if (!(word in glossary)) {
      changedWords.push(word);
    }
  }
  return chapter.paragraphsJp
    .map((text, index) => ({ text, index }))
    .filter((it) => {
      for (const word of changedWords) {
        if (it.text.includes(word)) return true;
      }
      return false;
    });
};

export interface TaskCallback {
  onStart: (total: number) => void;
  onChapterSuccess: (
    state: { [key in TranslatorId]?: number } & { jp?: number }
  ) => void;
  onChapterFailure: () => void;
  log: (message: any) => void;
}

export const translate = async (
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  accessToken: string | undefined,
  startIndex: number,
  endIndex: number,
  callback: TaskCallback
) => {
  let task: TranslateTaskDto;
  let translator: Translator | undefined = undefined;
  try {
    callback.log('获取元数据');
    task = await getTranslateTask(
      providerId,
      novelId,
      translatorId,
      startIndex,
      endIndex
    );

    try {
      const config: TranslatorConfig = {
        log: (message) => callback.log('　　' + message),
      };
      if (translatorId === 'gpt') {
        if (!accessToken) {
          throw Error('GPT翻译需要输入Token');
        } else {
          config.accessToken = accessToken;
        }
      } else {
        config.glossary = task.glossary;
      }
      translator = await createTranslator(translatorId, config);
    } catch (e: any) {
      callback.log(`发生错误，无法创建翻译器：${e}`);
      return;
    }

    const textsSrc = encodeMetadataToTranslate(task);
    if (textsSrc.length > 0) {
      if (translatorId === 'gpt') {
        callback.log('目前GPT翻译目录超级不稳定，跳过');
      } else {
        callback.log('翻译元数据');
        const textsDst = await translator.translate(textsSrc);

        callback.log(`上传元数据`);
        const metadataTranslated = decodeAsMetadataTranslated(task, textsDst);
        await postMetadata(
          providerId,
          novelId,
          translatorId,
          metadataTranslated
        );
      }
    }
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  const untranslatedChapters = Object.entries(task.untranslatedChapters);
  const expiredChapters = Object.entries(task.expiredChapters);
  callback.onStart(untranslatedChapters.length + expiredChapters.length);

  if (untranslatedChapters.length + expiredChapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const [index, chapterId] of untranslatedChapters) {
    try {
      callback.log(
        `\n获取章节[${index}] ${providerId}/${novelId}/${chapterId}`
      );
      const chapter = await getChapter(
        providerId,
        novelId,
        translatorId,
        chapterId
      );

      const textsSrc = chapter.paragraphsJp;
      callback.log(`翻译章节[${index}] ${providerId}/${novelId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);

      callback.log(`上传章节[${index}] ${providerId}/${novelId}/${chapterId}`);
      const state = await postChapter(
        providerId,
        novelId,
        translatorId,
        chapterId,
        {
          glossaryUuid: task.glossaryUuid,
          paragraphsZh: textsDst,
        }
      );
      callback.onChapterSuccess({ jp: state.jp, [translatorId]: state.zh });
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else {
        callback.log(`发生错误，跳过这个章节：${e}`);
        callback.onChapterFailure();
      }
    }
  }

  for (const [index, chapterId] of expiredChapters) {
    try {
      callback.log(
        `\n获取章节[${index}] ${providerId}/${novelId}/${chapterId}`
      );
      const chapter = await getChapter(
        providerId,
        novelId,
        translatorId,
        chapterId
      );
      const expiredParagraphs = getExpiredParagraphs(chapter, task.glossary);

      const textsSrc = expiredParagraphs.map((it) => it.text);
      const paragraphsZh: { [key: number]: string } = {};

      callback.log(`翻译章节[${index}] ${providerId}/${novelId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);
      expiredParagraphs.forEach((it, index) => {
        paragraphsZh[it.index] = textsDst[index];
      });

      callback.log(`上传章节[${index}] ${providerId}/${novelId}/${chapterId}`);
      const state = await putChapter(
        providerId,
        novelId,
        translatorId,
        chapterId,
        {
          glossaryUuid: task.glossaryUuid,
          paragraphsZh,
        }
      );
      callback.onChapterSuccess({ jp: state.jp, [translatorId]: state.zh });
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else {
        callback.log(`发生错误，跳过这个章节：${e}`);
        callback.onChapterFailure();
      }
    }
  }
};

interface CheckUpdateTask {
  chapters: { [key: number]: string };
}

async function getCheckUpdateTask(
  providerId: string,
  novelId: string,
  startIndex: number,
  endIndex: number
): Promise<CheckUpdateTask> {
  const url = `novel/${providerId}/${novelId}/check-update`;
  return api.get(url, { searchParams: { startIndex, endIndex } }).json();
}

async function checkChapterUpdate(
  providerId: string,
  novelId: string,
  chapterId: string
): Promise<string> {
  const url = `novel/${providerId}/${novelId}/check-update/${chapterId}`;
  return api.post(url).text();
}

export async function checkUpdate(
  providerId: string,
  novelId: string,
  startIndex: number,
  endIndex: number,
  callback: TaskCallback
) {
  let task: CheckUpdateTask;
  try {
    callback.log('开始检查章节更新');
    task = await getCheckUpdateTask(providerId, novelId, startIndex, endIndex);
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  const chapters = Object.entries(task.chapters);
  callback.onStart(chapters.length);

  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const [index, chapterId] of chapters) {
    try {
      callback.log(
        `\n检查章节[${index}] ${providerId}/${novelId}/${chapterId}`
      );
      const message = await checkChapterUpdate(providerId, novelId, chapterId);
      callback.log(message);
      callback.onChapterSuccess({});
    } catch (e) {
      callback.log(`发生错误，跳过这个章节：${e}`);
      callback.onChapterFailure();
    }
  }

  return;
}
