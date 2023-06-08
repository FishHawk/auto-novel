import { Options } from 'ky';

import api from './api';
import { Result, runCatching } from './result';
import { translate } from './api_web_novel_translate';
import { Page } from './page';

export interface WebNovelOutlineDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  extra?: string;
  total?: number;
  jp?: number;
  baidu?: number;
  youdao?: number;
}

async function list(
  page: number,
  pageSize: number,
  provider: string,
  query: string
): Promise<Result<Page<WebNovelOutlineDto>>> {
  return runCatching(
    api
      .get(`novel/list`, {
        searchParams: { page, pageSize, provider, query },
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
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: WebNovelTocItemDto[];
  visited: number;
  syncAt: number;
  favored?: boolean;
  jp?: number;
  baidu?: number;
  youdao?: number;
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

function createFileUrl(
  providerId: string,
  novelId: string,
  lang: 'jp' | 'zh-baidu' | 'zh-youdao' | 'mix-baidu' | 'mix-youdao',
  type: 'epub' | 'txt'
) {
  return `/api/novel/${providerId}/${novelId}/file/${lang}/${type}`;
}

export const ApiWebNovel = {
  list,
  listRank,
  getMetadata,
  putMetadata,
  putWenkuId,
  deleteWenkuId,
  getChapter,
  translate,
  createFileUrl,
};
