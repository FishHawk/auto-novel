import { Options } from 'ky';
import api from './api';
import { Ok, Err, Result } from './result';

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
  countJp: number;
  countZh: number;
}

async function list(
  page: number,
  provider: string,
  sort: string
): Promise<Result<BookListPageDto>> {
  return api
    .get(`novel/list`, {
      searchParams: { page, provider, sort },
    })
    .json<BookListPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function listFavorite(token: string): Promise<Result<BookListPageDto>> {
  return api
    .get(`novel/favorite`, {
      headers: { Authorization: 'Bearer ' + token },
    })
    .json<BookListPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function addFavorite(providerId: string, bookId: string, token: string) {
  return api
    .post(`novel/favorite-item`, {
      headers: { Authorization: 'Bearer ' + token },
      searchParams: { providerId, bookId },
    })
    .json<BookListPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function removeFavorite(
  providerId: string,
  bookId: string,
  token: string
) {
  return api
    .delete(`novel/favorite-item`, {
      headers: { Authorization: 'Bearer ' + token },
      searchParams: { providerId, bookId },
    })
    .json<BookListPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
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
  return api
    .get(`novel/rank/${providerId}`, { searchParams: options, timeout: 20000 })
    .json<BookListPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export interface BookStateDto {
  total: number;
  countJp: number;
  countZh: number;
}

async function getState(
  providerId: string,
  bookId: string
): Promise<Result<BookStateDto>> {
  return api
    .get(`novel/state/${providerId}/${bookId}`)
    .json<BookStateDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
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
  return api
    .get(`novel/metadata/${providerId}/${bookId}`, options)
    .json<BookMetadataDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export interface BookEpisodeDto {
  titleJp: string;
  titleZh: string | undefined;
  prevId: string | undefined;
  nextId: string | undefined;
  paragraphsJp: string[];
  paragraphsZh: string[] | undefined;
}

async function getEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<Result<BookEpisodeDto>> {
  return api
    .get(`novel/episode/${providerId}/${bookId}/${episodeId}`)
    .json<BookEpisodeDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export default {
  getState,
  list,
  listFavorite,
  addFavorite,
  removeFavorite,
  listRank,
  getMetadata,
  getEpisode,
};
