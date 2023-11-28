import { Err, Ok, Result, runCatching } from '@/data/result';

import { Favored } from './api_user';
import { client } from './client';
import { Page } from './common';
import { TranslatorId } from '../translator/translator';

export interface WenkuNovelOutlineDto {
  id: string;
  title: string;
  titleZh: string;
  cover: string;
}

export interface WenkuNovelDto {
  title: string;
  titleZh: string;
  cover: string;
  authors: string[];
  artists: string[];
  keywords: string[];
  r18: boolean;
  introduction: string;
  volumes: WenkuVolumeDto[];
  glossary: { [key: string]: string };
  visited: number;
  favored?: string;
  favoredList: Favored[];
  volumeZh: string[];
  volumeJp: VolumeJpDto[];
}

export interface WenkuVolumeDto {
  asin: string;
  title: string;
  titleZh?: string;
  cover: string;
}

export interface VolumeJpDto {
  volumeId: string;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
}

const listNovel = ({
  page,
  pageSize,
  query = '',
  level = 0,
}: {
  page: number;
  pageSize: number;
  query?: string;
  level?: number;
}) =>
  runCatching(
    client
      .get(`wenku`, { searchParams: { page, pageSize, query, level } })
      .json<Page<WenkuNovelOutlineDto>>()
  );

const listVolumesUser = () =>
  runCatching(
    client.get(`wenku/user`).json<{ list: VolumeJpDto[]; novelId: string }>()
  );

const getNovel = (novelId: string) =>
  runCatching(client.get(`wenku/${novelId}`).json<WenkuNovelDto>());

interface WenkuNovelCreateBody {
  title: string;
  titleZh: string;
  cover: string;
  authors: string[];
  artists: string[];
  r18: boolean;
  introduction: string;
  volumes: WenkuVolumeDto[];
}

const createNovel = (json: WenkuNovelCreateBody) =>
  runCatching(client.post(`wenku`, { json }).text());

const updateNovel = (id: string, json: WenkuNovelCreateBody) =>
  runCatching(client.put(`wenku/${id}`, { json }).text());

const updateGlossary = (id: string, json: { [key: string]: string }) =>
  runCatching(client.put(`wenku/${id}/glossary`, { json }).text());

const createVolume = (
  novelId: string,
  volumeId: string,
  type: 'jp' | 'zh',
  file: File,
  token: string,
  onProgress: (p: number) => void
) =>
  new Promise<Result<string>>(function (resolve, _reject) {
    const formData = new FormData();
    formData.append(type, file as File);

    let xhr = new XMLHttpRequest();

    xhr.open('POST', `/api/wenku/${novelId}/volume/${volumeId}`);

    xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    xhr.onload = function () {
      if (xhr.status === 200) {
        resolve(Ok(''));
      } else {
        resolve(Err(xhr.responseText));
      }
    };
    xhr.upload.addEventListener('progress', (e) => {
      const percent = e.lengthComputable ? (e.loaded / e.total) * 100 : 0;
      onProgress(Math.ceil(percent));
    });
    xhr.send(formData);
  });

const deleteVolume = (novelId: string, volumeId: string) =>
  runCatching(client.delete(`wenku/${novelId}/volume/${volumeId}`).text());

const createFileUrl = ({
  novelId,
  volumeId,
  lang,
  translationsMode,
  translations,
}: {
  novelId: string;
  volumeId: string;
  lang: 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: ('sakura' | 'baidu' | 'youdao' | 'gpt')[];
}) => {
  let filename = `${lang}.${translationsMode === 'parallel' ? 'B' : 'Y'}`;
  translations.forEach((it) => (filename += it[0]));
  filename += '.';
  filename += volumeId;

  const params = new URLSearchParams({
    translationsMode,
    lang,
    filename,
  });
  translations.forEach((it) => params.append('translations', it));
  const url = `/api/wenku/${novelId}/file/${volumeId}?${params}`;
  return { url, filename };
};

export const ApiWenkuNovel = {
  listNovel,
  listVolumesUser,
  //
  getNovel,
  //
  createNovel,
  updateNovel,
  updateGlossary,
  createVolume,
  deleteVolume,
  //
  createFileUrl,
};
