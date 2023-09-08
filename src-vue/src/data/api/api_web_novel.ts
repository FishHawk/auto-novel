import {
  TranslatorConfig,
  TranslatorId,
  createTranslator,
} from '@/data/translator/translator';
import { Translator } from '@/data/translator/base';

import { api } from './api';
import { Result, runCatching } from './result';
import { Page } from './page';

export interface WebNovelOutlineDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  type: string;
  attentions: string[];
  keywords: string[];
  extra?: string;
  total: number;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  updateAt?: number;
}

async function list(
  page: number,
  pageSize: number,
  query: string,
  provider: string,
  type: number,
  level: number,
  translate: number
): Promise<Result<Page<WebNovelOutlineDto>>> {
  return runCatching(
    api
      .get(`novel/list`, {
        searchParams: {
          page,
          pageSize,
          query,
          provider,
          type,
          level,
          translate,
        },
      })
      .json()
  );
}

async function listRank(
  providerId: string,
  options: { [key: string]: string }
): Promise<Result<Page<WebNovelOutlineDto>>> {
  return runCatching(
    api
      .get(`novel/rank/${providerId}`, {
        searchParams: options,
        timeout: 20000,
      })
      .json()
  );
}

const listReadHistory = (page: number, pageSize: number) =>
  runCatching(
    api
      .get('novel/read-history', { searchParams: { page, pageSize } })
      .json<Page<WebNovelOutlineDto>>()
  );

const listFavored = (
  page: number,
  pageSize: number,
  sort: 'create' | 'update'
) =>
  runCatching(
    api
      .get('novel/favored', { searchParams: { page, pageSize, sort } })
      .json<Page<WebNovelOutlineDto>>()
  );

export interface WebNovelTocItemDto {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
}

export interface WebNovelMetadataDto {
  wenkuId?: string;
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  type: string;
  attentions: string[];
  keywords: string[];
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: WebNovelTocItemDto[];
  visited: number;
  syncAt: number;
  favored?: boolean;
  lastReadChapterId?: string;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
}

const getMetadata = (providerId: string, novelId: string) =>
  runCatching(
    api.get(`novel/${providerId}/${novelId}`).json<WebNovelMetadataDto>()
  );

export interface WebNovelChapterDto {
  titleJp: string;
  titleZh: string | undefined;
  prevId: string | undefined;
  nextId: string | undefined;
  paragraphs: string[];
  baiduParagraphs: string[] | undefined;
  youdaoParagraphs: string[] | undefined;
  gptParagraphs: string[] | undefined;
}

async function getChapter(
  providerId: string,
  novelId: string,
  chapterId: string
): Promise<Result<WebNovelChapterDto>> {
  return runCatching(
    api.get(`novel/${providerId}/${novelId}/chapter/${chapterId}`).json()
  );
}

const putReadHistory = (
  providerId: string,
  novelId: string,
  chapterId: string
) =>
  runCatching(
    api
      .put(`novel/${providerId}/${novelId}/read-history`, { body: chapterId })
      .text()
  );

const putFavored = (providerId: string, novelId: string) =>
  runCatching(api.put(`novel/${providerId}/${novelId}/favored`).text());

const deleteFavored = (providerId: string, novelId: string) =>
  runCatching(api.delete(`novel/${providerId}/${novelId}/favored`).text());

async function updateMetadata(
  providerId: string,
  novelId: string,
  body: {
    title?: string;
    introduction?: string;
    toc: { [key: string]: string };
  }
): Promise<Result<WebNovelMetadataDto>> {
  return runCatching(
    api
      .post(`novel/${providerId}/${novelId}`, {
        json: body,
      })
      .json()
  );
}

async function updateGlossary(
  providerId: string,
  novelId: string,
  body: { [key: string]: string }
): Promise<Result<string>> {
  return runCatching(
    api
      .put(`novel/${providerId}/${novelId}/glossary`, {
        json: body,
      })
      .text()
  );
}

async function putWenkuId(
  providerId: string,
  novelId: string,
  wenkuId: string
): Promise<Result<string>> {
  return runCatching(
    api.put(`novel/${providerId}/${novelId}/wenku`, { body: wenkuId }).text()
  );
}

async function deleteWenkuId(
  providerId: string,
  novelId: string
): Promise<Result<string>> {
  return runCatching(api.delete(`novel/${providerId}/${novelId}/wenku`).text());
}

function createFileUrl(
  providerId: string,
  novelId: string,
  lang:
    | 'jp'
    | 'zh-baidu'
    | 'zh-youdao'
    | 'zh-gpt'
    | 'mix-baidu'
    | 'mix-youdao'
    | 'mix-gpt'
    | 'mix-all',
  type: 'epub' | 'txt'
) {
  return `/api/novel/${providerId}/${novelId}/file/${lang}/${type}`;
}

const translate = async (
  {
    providerId,
    novelId,
    translatorId,
    accessToken,
    startIndex,
    endIndex,
    translateExpireChapter,
    syncFromProvider,
  }: {
    providerId: string;
    novelId: string;
    translatorId: TranslatorId;
    accessToken?: string;
    startIndex: number;
    endIndex: number;
    translateExpireChapter: boolean;
    syncFromProvider: boolean;
  },
  callback: {
    onStart: (total: number) => void;
    onChapterSuccess: (state: {
      [key in TranslatorId | 'jp']?: number;
    }) => void;
    onChapterFailure: () => void;
    log: (message: any) => void;
  }
) => {
  // Api
  interface TranslateTaskDto {
    title?: string;
    introduction?: string;
    toc: string[];
    glossaryUuid?: string;
    glossary: { [key: string]: string };
    chapters: { [key: number]: 'untranslated' | 'translated' | 'expired' };
  }

  interface MetadataUpdateBody {
    title?: string;
    introduction?: string;
    toc: { [key: string]: string };
  }

  const endpoint = `novel/${providerId}/${novelId}/translate/${translatorId}`;
  const getTranslateTask = () => api.get(endpoint).json<TranslateTaskDto>();

  const updateMetadataTranslation = (json: MetadataUpdateBody) =>
    api.post(`${endpoint}/metadata`, { json }).text();

  const checkChapter = (chapterId: string) =>
    api
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
    api
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
  let translator: Translator;
  try {
    callback.log('获取元数据');
    task = await getTranslateTask();

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
        await updateMetadataTranslation(
          decodeAsMetadataTranslated(task, textsDst)
        );
      }
    }
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  const chapters = Object.entries(task.chapters)
    .map(([chapterId, state], index) => ({ index, chapterId, state }))
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
        callback.onChapterSuccess({ jp, [translatorId]: zh });
      }
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

export const ApiWebNovel = {
  list,
  listRank,
  listReadHistory,
  listFavored,
  //
  getMetadata,
  getChapter,
  //
  putReadHistory,
  putFavored,
  deleteFavored,
  updateMetadata,
  updateGlossary,
  putWenkuId,
  deleteWenkuId,
  //
  translate,
  createFileUrl,
};
