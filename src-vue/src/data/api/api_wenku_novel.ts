import ky from 'ky';
import api from './api';
import { Ok, Result, runCatching } from './result';

export interface WenkuListPageDto {
  pageNumber: number;
  items: WenkuListItemDto[];
}
export interface WenkuListItemDto {
  id: string;
  title: string;
  titleZh: string;
  cover: string;
}

async function list(
  page: number,
  query: string
): Promise<Result<WenkuListPageDto>> {
  return runCatching(
    api
      .get(`wenku/list`, {
        searchParams: { page, query },
      })
      .json()
  );
}

export interface WenkuMetadataDto {
  bookId: string;
  title: string;
  titleZh: string;
  titleZhAlias: string[];
  cover: string;
  authors: string[];
  artists: string[];
  keywords: string[];
  introduction: string;
  visited: number;
  files: string[];
}

async function getMetadata(novelId: string): Promise<Result<WenkuMetadataDto>> {
  return runCatching(api.get(`wenku/${novelId}`).json());
}

interface MetadataCreateBody {
  title: string;
  titleZh: string;
  titleZhAlias: string[];
  cover: string;
  coverSmall: string;
  authors: string[];
  artists: string[];
  keywords: string[];
  introduction: string;
}

async function postMetadata(
  body: MetadataCreateBody,
  token: string
): Promise<Result<String>> {
  return runCatching(
    api
      .post(`wenku`, {
        json: body,
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

async function putMetadata(
  id: string,
  body: MetadataCreateBody,
  token: string
): Promise<Result<String>> {
  return runCatching(
    api
      .put(`wenku/${id}`, {
        json: body,
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

interface BangumiSection {
  name: string;
  name_cn: string;
  images: {
    common: string;
    grid: string;
    large: string;
    medium: string;
    small: string;
  };
  infobox: { key: string; value: string }[];
  summary: string;
  tags: { name: string; count: number }[];
}

async function getMetadataFromBangumi(
  bookId: string
): Promise<Result<MetadataCreateBody>> {
  const sectionResult = await runCatching(
    ky.get(`https://api.bgm.tv/v0/subjects/${bookId}`).json<BangumiSection>()
  );
  if (sectionResult.ok) {
    const metadata: MetadataCreateBody = {
      title: sectionResult.value.name,
      titleZh: sectionResult.value.name_cn,
      titleZhAlias: [],
      cover: sectionResult.value.images.medium,
      coverSmall: sectionResult.value.images.small,
      authors: [],
      artists: [],
      keywords: sectionResult.value.tags.map((it) => it.name),
      introduction: sectionResult.value.summary,
    };
    sectionResult.value.infobox.forEach((it) => {
      if (it.key == '作者') {
        metadata.authors.push(it.value);
      } else if (it.key == '插图') {
        metadata.artists.push(it.value);
      }
    });
    return Ok(metadata);
  } else {
    return sectionResult;
  }
}

function createUploadUrl(bookId: string) {
  return `/api/wenku/${bookId}/episode`;
}

export default {
  list,
  getMetadata,
  postMetadata,
  putMetadata,
  getMetadataFromBangumi,
  createUploadUrl,
};
