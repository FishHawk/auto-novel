import { Err, Ok, Result, runCatching } from '@/data/result';

import { client } from './client';
import { Page } from './common';

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
    client
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
    client
      .get('wenku/favored', { searchParams: { page, pageSize, sort } })
      .json<Page<WenkuNovelOutlineDto>>()
  );

export interface WenkuVolumeDto {
  asin: string;
  title: string;
  titleZh?: string;
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
  runCatching(client.get(`wenku/non-archived`).json<VolumeJpDto[]>());

const listVolumesUser = () =>
  runCatching(
    client.get(`wenku/user`).json<{ list: VolumeJpDto[]; novelId: string }>()
  );

const getNovel = (novelId: string) =>
  runCatching(client.get(`wenku/${novelId}`).json<WenkuNovelDto>());

const putFavored = (novelId: string) =>
  runCatching(client.put(`wenku/${novelId}/favored`).text());

const deleteFavored = (novelId: string) =>
  runCatching(client.delete(`wenku/${novelId}/favored`).text());

export interface WenkuNovelCreateBody {
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
  createNovel,
  updateNovel,
  //
  updateGlossary,
  //
  createVolume,
  deleteVolume,
};
