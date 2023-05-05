import { Options } from 'ky';
import api from './api';
import { Result, runCatching } from './result';

export interface BookListPageDto {
  pageNumber: number;
  items: BookListItemDto[];
}

export interface BookListItemDto {
  providerId: string;
  bookId: string;
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
): Promise<Result<BookListPageDto>> {
  return runCatching(
    api
      .get(`novel/list`, {
        searchParams: { page, pageSize, provider, query },
      })
      .json()
  );
}

async function listFavorite(
  page: number,
  pageSize: number,
  token: string
): Promise<Result<BookListPageDto>> {
  return runCatching(
    api
      .get(`novel/favorite`, {
        headers: { Authorization: 'Bearer ' + token },
        searchParams: { page, pageSize },
      })
      .json()
  );
}

async function addFavorite(providerId: string, bookId: string, token: string) {
  return runCatching(
    api
      .post(`novel/favorite-item`, {
        headers: { Authorization: 'Bearer ' + token },
        searchParams: { providerId, bookId },
      })
      .json()
  );
}

async function removeFavorite(
  providerId: string,
  bookId: string,
  token: string
) {
  return runCatching(
    api
      .delete(`novel/favorite-item`, {
        headers: { Authorization: 'Bearer ' + token },
        searchParams: { providerId, bookId },
      })
      .json()
  );
}

export interface BookRankPageDto {
  pageNumber: number;
  items: BookRankItemDto[];
}

export interface BookRankItemDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
  extra: string;
}

async function listRank(
  providerId: string,
  options: { [key: string]: string }
): Promise<Result<BookListPageDto>> {
  return runCatching(
    api
      .get(`novel/rank/${providerId}`, {
        searchParams: options,
        timeout: 20000,
      })
      .json()
  );
}

export interface BookStateDto {
  total: number;
  count: number;
  countBaidu: number;
  countYoudao: number;
}

async function getState(
  providerId: string,
  bookId: string
): Promise<Result<BookStateDto>> {
  return runCatching(api.get(`novel/${providerId}/${bookId}/state`).json());
}

export interface BookTocItemDto {
  titleJp: string;
  titleZh?: string;
  episodeId?: string;
}

export interface BookMetadataDto {
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: BookTocItemDto[];
  visited: number;
  downloaded: number;
  syncAt: number;
  inFavorite?: boolean;
}

async function getMetadata(
  providerId: string,
  bookId: string,
  token: string | undefined
): Promise<Result<BookMetadataDto>> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  return runCatching(
    api.get(`novel/${providerId}/${bookId}`, options).json()
  );
}

interface BookMetadataPatchBody {
  title?: string;
  introduction?: string;
  glossary?: { [key: string]: string };
  toc: { [key: string]: string };
}

async function putMetadata(
  providerId: string,
  bookId: string,
  patch: BookMetadataPatchBody,
  token: string
): Promise<Result<BookMetadataDto>> {
  return runCatching(
    api
      .put(`novel/${providerId}/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
        json: patch,
      })
      .json()
  );
}

export interface BookEpisodeDto {
  titleJp: string;
  titleZh: string | undefined;
  prevId: string | undefined;
  nextId: string | undefined;
  paragraphs: string[];
  baiduParagraphs: string[] | undefined;
  youdaoParagraphs: string[] | undefined;
}

async function getEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<Result<BookEpisodeDto>> {
  return runCatching(
    api.get(`novel/${providerId}/${bookId}/episode/${episodeId}`).json()
  );
}

export default {
  getState,
  list,
  listFavorite,
  addFavorite,
  removeFavorite,
  listRank,
  getMetadata,
  putMetadata,
  getEpisode,
};
