import { Page } from '@/model/Page';
import {
  WebNovelChapterDto,
  WebNovelDto,
  WebNovelOutlineDto,
} from '@/model/WebNovel';

import { client } from './client';

const listNovel = ({
  page,
  pageSize,
  query = '',
  provider = '',
  type = 0,
  level = 0,
  translate = 0,
  sort = 0,
}: {
  page: number;
  pageSize: number;
  query?: string;
  provider?: string;
  type?: number;
  level?: number;
  translate?: number;
  sort?: number;
}) =>
  client
    .get(`novel`, {
      searchParams: {
        page,
        pageSize,
        query,
        provider,
        type,
        level,
        translate,
        sort,
      },
    })
    .json<Page<WebNovelOutlineDto>>();

const listRank = (providerId: string, params: { [key: string]: string }) =>
  client
    .get(`novel/rank/${providerId}`, {
      searchParams: params,
      timeout: 20000,
    })
    .json<Page<WebNovelOutlineDto>>();

const getNovel = (providerId: string, novelId: string) =>
  client.get(`novel/${providerId}/${novelId}`).json<WebNovelDto>();

const getChapter = (providerId: string, novelId: string, chapterId: string) =>
  client
    .get(`novel/${providerId}/${novelId}/chapter/${chapterId}`)
    .json<WebNovelChapterDto>();

const updateNovel = (
  providerId: string,
  novelId: string,
  json: {
    title: string;
    introduction: string;
    toc: { [key: string]: string };
    wenkuId?: string;
  }
) => client.post(`novel/${providerId}/${novelId}`, { json });

const updateGlossary = (
  providerId: string,
  novelId: string,
  json: { [key: string]: string }
) => client.put(`novel/${providerId}/${novelId}/glossary`, { json });

const createFileUrl = ({
  providerId,
  novelId,
  lang,
  translationsMode,
  translations,
  type,
  title,
}: {
  providerId: string;
  novelId: string;
  lang: 'jp' | 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: ('sakura' | 'baidu' | 'youdao' | 'gpt')[];
  type: 'epub' | 'txt';
  title: string;
}) => {
  const filename = [
    lang,
    lang === 'jp'
      ? ''
      : (translationsMode === 'parallel' ? 'B' : 'Y') +
        translations.map((it) => it[0]).join(''),
    title.replace(/[\/|\\:*?"<>]/g, ''),
    type,
  ]
    .filter(Boolean)
    .join('.');

  const params = new URLSearchParams({
    lang,
    translationsMode,
    type,
    filename,
  });
  translations.forEach((it) => params.append('translations', it));

  const url = `/api/novel/${providerId}/${novelId}/file?${params}`;
  return { url, filename };
};

export const WebNovelRepository = {
  listNovel,
  listRank,

  getNovel,
  getChapter,

  updateNovel,
  updateGlossary,

  createFileUrl,
};
