import { runCatching } from '@/data/result';

import { Favored } from './api_user';
import { client } from './client';
import { Page } from './common';

export interface WebNovelOutlineDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  type: string;
  attentions: string[];
  keywords: string[];
  extra?: string;
  total: number;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  updateAt?: number;
}

export interface WebNovelTocItemDto {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
}

export interface WebNovelDto {
  wenkuId?: string;
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  type: string;
  attentions: string[];
  keywords: string[];
  points?: number;
  totalCharacters?: number;
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: WebNovelTocItemDto[];
  visited: number;
  syncAt: number;
  favored?: string;
  favoredList: Favored[];
  lastReadChapterId?: string;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
}

export interface WebNovelChapterDto {
  titleJp: string;
  titleZh?: string;
  prevId?: string;
  nextId?: string;
  paragraphs: string[];
  baiduParagraphs?: string[];
  youdaoParagraphs?: string[];
  gptParagraphs?: string[];
  sakuraParagraphs?: string[];
}

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
  runCatching(
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
      .json<Page<WebNovelOutlineDto>>()
  );

const listRank = (providerId: string, params: { [key: string]: string }) =>
  runCatching(
    client
      .get(`novel/rank/${providerId}`, {
        searchParams: params,
        timeout: 20000,
      })
      .json<Page<WebNovelOutlineDto>>()
  );

const getNovel = (providerId: string, novelId: string) =>
  runCatching(client.get(`novel/${providerId}/${novelId}`).json<WebNovelDto>());

const getChapter = (providerId: string, novelId: string, chapterId: string) =>
  runCatching(
    client
      .get(`novel/${providerId}/${novelId}/chapter/${chapterId}`)
      .json<WebNovelChapterDto>()
  );

const updateNovel = (
  providerId: string,
  novelId: string,
  json: {
    title: string;
    introduction: string;
    toc: { [key: string]: string };
    wenkuId?: string;
  }
) => {
  return runCatching(
    client.post(`novel/${providerId}/${novelId}`, { json }).json<WebNovelDto>()
  );
};

const updateGlossary = (
  providerId: string,
  novelId: string,
  json: { [key: string]: string }
) =>
  runCatching(
    client.put(`novel/${providerId}/${novelId}/glossary`, { json }).text()
  );

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

export const ApiWebNovel = {
  listNovel,
  listRank,
  //
  getNovel,
  getChapter,
  //
  updateNovel,
  updateGlossary,
  //
  createFileUrl,
};
