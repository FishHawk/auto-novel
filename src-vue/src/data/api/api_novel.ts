import api from './api';
import { Ok, Err, Result } from './result';

export interface BookListItemDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
  extra: string;
}

export interface BookPageDto {
  pageNumber: number;
  items: BookListItemDto[];
}

async function list(
  page: number,
  provider: string,
  sort: string
): Promise<Result<BookPageDto>> {
  return api
    .get(`novel/list`, {
      searchParams: { page, provider, sort },
    })
    .json<BookPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function listRank(
  providerId: string,
  options: { [key: string]: string }
): Promise<Result<BookPageDto>> {
  return api
    .get(`novel/rank/${providerId}`, { searchParams: options, timeout: 20000 })
    .json<BookPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export interface BookStateDto {
  total: number;
  countJp: number;
  countZh: number;
}

export interface BookMetadataDto {
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: { titleJp: string; titleZh?: string; episodeId?: string }[];
  visited: number;
  downloaded: number;
  syncAt: number;
}

export interface BookEpisodeDto {
  titleJp: string;
  titleZh: string | undefined;
  prevId: string | undefined;
  nextId: string | undefined;
  paragraphsJp: string[];
  paragraphsZh: string[] | undefined;
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

async function getMetadata(
  providerId: string,
  bookId: string
): Promise<Result<BookMetadataDto>> {
  return api
    .get(`novel/metadata/${providerId}/${bookId}`)
    .json<BookMetadataDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
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

export interface BookFiles {
  label: string;
  lang: string;
  files: { label: string; url: string; name: string }[];
}

export default {
  getState,
  list,
  listRank,
  getMetadata,
  getEpisode,
};
