import ky, { Options } from 'ky';

import api from './api';
import { Ok, Result, runCatching } from './result';
import { translate } from './api_wenku_novel_translate';
import { Page } from './page';

export interface WenkuNovelOutlineDto {
  id: string;
  title: string;
  titleZh: string;
  cover: string;
}

async function list(
  page: number,
  query: string
): Promise<Result<Page<WenkuNovelOutlineDto>>> {
  return runCatching(
    api
      .get(`wenku/list`, {
        searchParams: { page, query },
      })
      .json()
  );
}

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

async function listVolumesNonArchived(): Promise<Result<VolumeJpDto[]>> {
  return runCatching(api.get(`wenku/non-archived`).json());
}
async function listVolumesUser(
  token: string
): Promise<Result<{ list: VolumeJpDto[]; novelId: string }>> {
  return runCatching(
    api
      .get(`wenku/user`, { headers: { Authorization: 'Bearer ' + token } })
      .json()
  );
}

async function getMetadata(
  novelId: string,
  token: string | undefined
): Promise<Result<WenkuMetadataDto>> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  return runCatching(api.get(`wenku/${novelId}`, options).json());
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

async function patchMetadata(
  id: string,
  body: MetadataCreateBody,
  token: string
): Promise<Result<String>> {
  return runCatching(
    api
      .patch(`wenku/${id}`, {
        json: body,
        headers: { Authorization: 'Bearer ' + token },
      })
      .text()
  );
}

async function notifyUpdate(
  id: string,
  token: string
): Promise<Result<String>> {
  return runCatching(
    api
      .post(`wenku/${id}/notify-update`, {
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
  novelId: string
): Promise<Result<MetadataCreateBody>> {
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
}

function createVolumeZhUploadUrl(novelId: string) {
  return `/api/wenku/${novelId}/volume-zh`;
}

function createVolumeJpUploadUrl(novelId: string) {
  return `/api/wenku/${novelId}/volume-jp`;
}

function createFileUrl(
  novelId: string,
  volumeId: string,
  lang:
    | 'zh-baidu'
    | 'zh-youdao'
    | 'zh-gpt'
    | 'mix-baidu'
    | 'mix-youdao'
    | 'mix-gpt'
) {
  return `/api/wenku/${novelId}/file/${volumeId}/${lang}`;
}

export const ApiWenkuNovel = {
  list,
  listVolumesUser,
  listVolumesNonArchived,
  getMetadata,
  postMetadata,
  patchMetadata,
  notifyUpdate,
  getMetadataFromBangumi,
  createVolumeZhUploadUrl,
  createVolumeJpUploadUrl,
  translate,
  createFileUrl,
};
