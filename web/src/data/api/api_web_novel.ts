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

async function list({
  page,
  pageSize,
  query = '',
  provider = '',
  type = 0,
  level = 0,
  translate = 0,
}: {
  page: number;
  pageSize: number;
  query?: string;
  provider?: string;
  type?: number;
  level?: number;
  translate?: number;
}): Promise<Result<Page<WebNovelOutlineDto>>> {
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

const listReadHistory = ({
  page,
  pageSize,
}: {
  page: number;
  pageSize: number;
}) =>
  runCatching(
    api
      .get('novel/read-history', { searchParams: { page, pageSize } })
      .json<Page<WebNovelOutlineDto>>()
  );

const listFavored = ({
  page,
  pageSize,
  sort = 'update',
}: {
  page: number;
  pageSize: number;
  sort?: 'create' | 'update';
}) =>
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
  createFileUrl,
};
