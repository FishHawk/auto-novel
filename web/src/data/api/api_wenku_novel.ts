import ky from 'ky';

import { api } from './api';
import { Ok, runCatching } from './result';
import { Page } from './page';

export interface WenkuNovelOutlineDto {
  id: string;
  title: string;
  titleZh: string;
  cover: string;
}

const list = ({
  page,
  pageSize,
  query = '',
}: {
  page: number;
  pageSize: number;
  query?: string;
}) =>
  runCatching(
    api
      .get(`wenku`, { searchParams: { page, pageSize, query } })
      .json<Page<WenkuNovelOutlineDto>>()
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
      .get('wenku/favored', { searchParams: { page, pageSize, sort } })
      .json<Page<WenkuNovelOutlineDto>>()
  );

export interface WenkuMetadataDto {
  title: string;
  titleZh: string;
  cover: string;
  coverSmall: string;
  authors: string[];
  artists: string[];
  keywords: string[];
  introduction: string;
  glossary: { [key: string]: string };
  visited: number;
  favored?: boolean;
  volumeZh: string[];
  volumeJp: VolumeJpDto[];
}

export interface VolumeJpDto {
  volumeId: string;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
}

const listVolumesNonArchived = () =>
  runCatching(api.get(`wenku/non-archived`).json<VolumeJpDto[]>());

const listVolumesUser = () =>
  runCatching(
    api.get(`wenku/user`).json<{ list: VolumeJpDto[]; novelId: string }>()
  );

const getNovel = (novelId: string) =>
  runCatching(api.get(`wenku/${novelId}`).json<WenkuMetadataDto>());

const putFavored = (novelId: string) =>
  runCatching(api.put(`wenku/${novelId}/favored`).text());

const deleteFavored = (novelId: string) =>
  runCatching(api.delete(`wenku/${novelId}/favored`).text());

interface NovelCreateBody {
  title: string;
  titleZh: string;
  cover: string;
  coverSmall: string;
  authors: string[];
  artists: string[];
  keywords: string[];
  introduction: string;
}

const createNovel = (json: NovelCreateBody) =>
  runCatching(api.post(`wenku`, { json }).text());

const updateNovel = (id: string, json: NovelCreateBody) =>
  runCatching(api.put(`wenku/${id}`, { json }).text());

const updateGlossary = (id: string, json: { [key: string]: string }) =>
  runCatching(api.put(`wenku/${id}/glossary`, { json }).text());

const getNovelFromBangumi = async (novelId: string) => {
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

  const sectionResult = await runCatching(
    ky.get(`https://api.bgm.tv/v0/subjects/${novelId}`).json<BangumiSection>()
  );
  if (sectionResult.ok) {
    const metadata: NovelCreateBody = {
      title: sectionResult.value.name,
      titleZh: sectionResult.value.name_cn,
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
};

const createVolumeZhUploadUrl = (novelId: string, volumeId: string) =>
  `/api/wenku/${novelId}/volume-zh/${volumeId}`;

const createVolumeJpUploadUrl = (novelId: string, volumeId: string) =>
  `/api/wenku/${novelId}/volume-jp/${volumeId}`;

export const ApiWenkuNovel = {
  list,
  listFavored,
  listVolumesUser,
  listVolumesNonArchived,
  //
  getNovel,
  //
  putFavored,
  deleteFavored,
  //
  getNovelFromBangumi,
  createNovel,
  updateNovel,
  //
  updateGlossary,
  createVolumeZhUploadUrl,
  createVolumeJpUploadUrl,
};
