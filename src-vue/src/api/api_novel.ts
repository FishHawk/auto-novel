import ky from 'ky';
import { Ok, Err, Result } from './result';

export interface BookStateDto {
  total: number;
  countJp: number;
  countZh: number;
}

export interface BookPageItemDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh: string | undefined;
  state: BookStateDto;
}

export interface BookPageDto {
  total: number;
  items: BookPageItemDto[];
}

export interface BookMetadataDto {
  providerId: string;
  bookId: string;
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  introductionJp: string;
  introductionZh?: string;
  toc: { titleJp: string; titleZh?: string; episodeId?: string }[];
  visited: number;
  downloaded: number;
  syncAt: string;
  changeAt: string;
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
  return ky
    .get(`/api/novel/state/${providerId}/${bookId}`)
    .json<BookStateDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function list(
  page: number,
  provider: string,
  sort: 'created' | 'changed'
): Promise<Result<BookPageDto>> {
  return ky
    .get(`/api/novel/list`, {
      searchParams: { page, provider, sort },
    })
    .json<BookPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function getMetadata(
  providerId: string,
  bookId: string
): Promise<Result<BookMetadataDto>> {
  return ky
    .get(`/api/novel/metadata/${providerId}/${bookId}`)
    .json<BookMetadataDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function getEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<Result<BookEpisodeDto>> {
  return ky
    .get(`/api/novel/episode/${providerId}/${bookId}/${episodeId}`)
    .json<BookEpisodeDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export default {
  getState,
  list,
  getMetadata,
  getEpisode,
};

export interface BookFiles {
  label: string;
  lang: string;
  files: { label: string; url: string; name: string }[];
}

export function stateToFileList(
  providerId: string,
  bookId: string,
  state: BookStateDto
): BookFiles[] {
  const baseUrl = window.location.origin + `/api/prepare-book/`;

  function createFile(label: string, lang: string, type: string) {
    return {
      label,
      url: baseUrl + `${providerId}/${bookId}/${lang}/${type}`,
      name: `${providerId}.${bookId}.${lang}.${type}`,
    };
  }

  return [
    {
      label: `日文(${state.countJp}/${state.total})`,
      lang: 'jp',
      files: [createFile('TXT', 'jp', 'txt'), createFile('EPUB', 'jp', 'epub')],
    },
    {
      label: `中文(${state.countZh}/${state.total})`,
      lang: 'zh',
      files: [
        createFile('TXT', 'zh', 'txt'),
        createFile('EPUB', 'zh', 'epub'),
        createFile('中日对比版TXT', 'mix', 'txt'),
        createFile('中日对比版EPUB', 'mix', 'epub'),
      ],
    },
  ];
}
