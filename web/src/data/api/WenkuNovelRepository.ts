import { Page } from '@/model/Page';
import {
  WenkuNovelDto,
  WenkuNovelOutlineDto,
  WenkuVolumeDto,
} from '@/model/WenkuNovel';

import { client } from './client';

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
  client
    .get(`wenku`, { searchParams: { page, pageSize, query, level } })
    .json<Page<WenkuNovelOutlineDto>>();

const getNovel = (novelId: string) =>
  client.get(`wenku/${novelId}`).json<WenkuNovelDto>();

interface WenkuNovelCreateBody {
  title: string;
  titleZh: string;
  cover: string;
  authors: string[];
  artists: string[];
  r18: boolean;
  introduction: string;
  keywords: string[];
  volumes: WenkuVolumeDto[];
}

const createNovel = (json: WenkuNovelCreateBody) =>
  client.post(`wenku`, { json });

const updateNovel = (id: string, json: WenkuNovelCreateBody) =>
  client.put(`wenku/${id}`, { json });

const updateGlossary = (id: string, json: { [key: string]: string }) =>
  client.put(`wenku/${id}/glossary`, { json });

const createVolume = (
  novelId: string,
  volumeId: string,
  type: 'jp' | 'zh',
  file: File,
  token: string,
  onProgress: (p: number) => void
) =>
  new Promise<void>(function (resolve, reject) {
    const formData = new FormData();
    formData.append(type, file as File);

    let xhr = new XMLHttpRequest();

    xhr.open(
      'POST',
      `/api/wenku/${novelId}/volume/${encodeURIComponent(volumeId)}`
    );

    xhr.setRequestHeader('Authorization', 'Bearer ' + token);
    xhr.onload = function () {
      if (xhr.status === 200) {
        resolve();
      } else {
        reject(new Error(xhr.responseText));
      }
    };
    xhr.upload.addEventListener('progress', (e) => {
      const percent = e.lengthComputable ? (e.loaded / e.total) * 100 : 0;
      onProgress(Math.ceil(percent));
    });
    xhr.send(formData);
  });

const deleteVolume = (novelId: string, volumeId: string) =>
  client.delete(`wenku/${novelId}/volume/${encodeURIComponent(volumeId)}`);

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
  const filename = [
    lang,
    (translationsMode === 'parallel' ? 'B' : 'Y') +
      translations.map((it) => it[0]).join(''),
    volumeId,
  ].join('.');

  const params = new URLSearchParams({
    lang,
    translationsMode,
    filename,
  });
  translations.forEach((it) => params.append('translations', it));
  const url = `/api/wenku/${novelId}/file/${volumeId}?${params}`;
  return { url, filename };
};

export const WenkuNovelRepository = {
  listNovel,
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
