import { Options } from 'ky';
import api from './api';
import { Result, runCatching } from './result';

export interface WebNovelListPageDto {
  pageNumber: number;
  items: WebNovelListItemDto[];
}

export interface WebNovelListItemDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  total: number;
  count: number;
  countBaidu: number;
  countYoudao: number;
}

async function list(
  page: number,
  pageSize: number,
  provider: string,
  query: string
): Promise<Result<WebNovelListPageDto>> {
  return runCatching(
    api
      .get(`novel/list`, {
        searchParams: { page, pageSize, provider, query },
      })
      .json()
  );
}

export interface WebNovelRankPageDto {
  pageNumber: number;
  items: WebNovelRankItemDto[];
}

export interface WebNovelRankItemDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  extra: string;
}

async function listRank(
  providerId: string,
  options: { [key: string]: string }
): Promise<Result<WebNovelListPageDto>> {
  return runCatching(
    api
      .get(`novel/rank/${providerId}`, {
        searchParams: options,
        timeout: 20000,
      })
      .json()
  );
}

export interface WebNovelStateDto {
  total: number;
  count: number;
  countBaidu: number;
  countYoudao: number;
}

async function getState(
  providerId: string,
  novelId: string
): Promise<Result<WebNovelStateDto>> {
  return runCatching(api.get(`novel/${providerId}/${novelId}/state`).json());
}

export interface WebNovelTocItemDto {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
}

export interface WebNovelMetadataDto {
  wenkuId?: string;
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: WebNovelTocItemDto[];
  visited: number;
  syncAt: number;
  favored?: boolean;
}

async function getMetadata(
  providerId: string,
  novelId: string,
  token: string | undefined
): Promise<Result<WebNovelMetadataDto>> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  return runCatching(api.get(`novel/${providerId}/${novelId}`, options).json());
}

async function putWenkuId(
  providerId: string,
  novelId: string,
  wenkuId: string,
  token: string
): Promise<Result<WebNovelMetadataDto>> {
  return runCatching(
    api
      .put(`novel/${providerId}/${novelId}/wenku`, {
        headers: { Authorization: 'Bearer ' + token },
        body: wenkuId,
      })
      .json()
  );
}

async function deleteWenkuId(
  providerId: string,
  novelId: string,
  token: string
): Promise<Result<WebNovelMetadataDto>> {
  return runCatching(
    api
      .delete(`novel/${providerId}/${novelId}/wenku`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

interface WebNovelMetadataPatchBody {
  title?: string;
  introduction?: string;
  glossary?: { [key: string]: string };
  toc: { [key: string]: string };
}

async function putMetadata(
  providerId: string,
  novelId: string,
  patch: WebNovelMetadataPatchBody,
  token: string
): Promise<Result<WebNovelMetadataDto>> {
  return runCatching(
    api
      .put(`novel/${providerId}/${novelId}`, {
        headers: { Authorization: 'Bearer ' + token },
        json: patch,
      })
      .json()
  );
}

export interface WebNovelChapterDto {
  titleJp: string;
  titleZh: string | undefined;
  prevId: string | undefined;
  nextId: string | undefined;
  paragraphs: string[];
  baiduParagraphs: string[] | undefined;
  youdaoParagraphs: string[] | undefined;
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

export default {
  getState,
  list,
  listRank,
  getMetadata,
  putMetadata,
  putWenkuId,
  deleteWenkuId,
  getChapter,
};
