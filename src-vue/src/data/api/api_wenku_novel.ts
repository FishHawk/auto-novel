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

const list = (page: number, query: string) =>
  runCatching(
    api
      .get(`wenku/list`, { searchParams: { page, query } })
      .json<Page<WenkuNovelOutlineDto>>()
  );

const listFavored = (
  page: number,
  pageSize: number,
  sort: 'create' | 'update'
) =>
  runCatching(
    api
      .get('wenku/favored', { searchParams: { page, pageSize, sort } })
      .json<Page<WenkuNovelOutlineDto>>()
  );

export interface WenkuMetadataDto {
  title: string;
  titleZh: string;
  titleZhAlias: string[];
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

const getMetadata = (novelId: string) =>
  runCatching(api.get(`wenku/${novelId}`).json<WenkuMetadataDto>());

const putFavored = (novelId: string) =>
  runCatching(api.put(`wenku/${novelId}/favored`).text());

const deleteFavored = (novelId: string) =>
  runCatching(api.delete(`wenku/${novelId}/favored`).text());

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

const postMetadata = (body: MetadataCreateBody) =>
  runCatching(api.post(`wenku`, { json: body }).text());

const patchMetadata = (id: string, body: MetadataCreateBody) =>
  runCatching(api.patch(`wenku/${id}`, { json: body }).text());

const updateGlossary = (id: string, body: { [key: string]: string }) =>
  runCatching(api.put(`wenku/${id}/glossary`, { json: body }).text());

const notifyUpdate = (id: string) =>
  runCatching(api.post(`wenku/${id}/notify-update`).text());

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

const getMetadataFromBangumi = async (novelId: string) => {
  const sectionResult = await runCatching(
    ky.get(`https://api.bgm.tv/v0/subjects/${novelId}`).json<BangumiSection>()
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
};

const createVolumeZhUploadUrl = (novelId: string) =>
  `/api/wenku/${novelId}/volume-zh`;

const createVolumeJpUploadUrl = (novelId: string) =>
  `/api/wenku/${novelId}/volume-jp`;

const createFileUrl = (
  novelId: string,
  volumeId: string,
  lang:
    | 'zh-baidu'
    | 'zh-youdao'
    | 'zh-gpt'
    | 'mix-baidu'
    | 'mix-youdao'
    | 'mix-gpt'
) => `/api/wenku/${novelId}/file/${volumeId}/${lang}`;

export const ApiWenkuNovel = {
  list,
  listFavored,
  listVolumesUser,
  listVolumesNonArchived,
  //
  getMetadata,
  //
  putFavored,
  deleteFavored,
  postMetadata,
  patchMetadata,
  updateGlossary,
  notifyUpdate,
  getMetadataFromBangumi,
  createVolumeZhUploadUrl,
  createVolumeJpUploadUrl,
  //
  createFileUrl,
};
