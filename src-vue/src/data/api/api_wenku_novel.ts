import ky, { Options } from 'ky';
import api from './api';
import { Err, Ok, Result, runCatching } from './result';
import { TranslatorAdapter } from '../translator/adapter';
import { YoudaoTranslator } from '../translator/youdao';
import { BaiduTranslator } from '../translator/baidu';

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
  files: string[];
  favored?: boolean;
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

export interface VolumeStateDto {
  fileName: string;
  jp: number;
  baidu: number;
  youdao: number;
}

async function listNonArchived(): Promise<Result<VolumeStateDto[]>> {
  return runCatching(api.get('wenku/non-archived').json());
}

function createNonArchivedUploadUrl() {
  return '/api/wenku/non-archived';
}

export interface ChapterStateDto {
  chapterId: string;
  baidu: boolean;
  youdao: boolean;
}

function getNonArchivedState(
  fileName: string
): Promise<Result<ChapterStateDto[]>> {
  return runCatching(api.get(`wenku/non-archived/${fileName}`).json());
}

function getNonArchivedEpubInfo(fileName: string): Promise<ChapterStateDto[]> {
  return api.get(`wenku/non-archived/${fileName}`).json();
}

function getNonArchivedChapter(
  fileName: string,
  chapterId: string
): Promise<string[]> {
  return api.get(`wenku/non-archived/${fileName}/${chapterId}`).json();
}

function postNonArchivedChapter(
  fileName: string,
  chapterId: string,
  version: 'baidu' | 'youdao',
  content: string[]
): Promise<string[]> {
  return api
    .post(`wenku/non-archived/${fileName}/${chapterId}/${version}`, {
      json: content,
    })
    .json();
}

interface UpdateCallback {
  onStart: (total: number) => void;
  onEpisodeTranslateSuccess: () => void;
  onEpisodeTranslateFailure: () => void;
}

async function update(
  version: 'baidu' | 'youdao',
  fileName: string,
  callback: UpdateCallback
): Promise<Result<undefined, any>> {
  let total: number;
  let chapterIds: string[];
  let translator: TranslatorAdapter | undefined = undefined;
  try {
    console.log(`获取元数据 ${fileName}`);
    const state = await getNonArchivedEpubInfo(fileName);
    total = state.length;

    try {
      if (version === 'baidu') {
        translator = new TranslatorAdapter(await BaiduTranslator.create(), {});
        chapterIds = state.filter((it) => !it.baidu).map((it) => it.chapterId);
      } else {
        translator = new TranslatorAdapter(await YoudaoTranslator.create(), {});
        chapterIds = state.filter((it) => !it.youdao).map((it) => it.chapterId);
      }
    } catch (e: any) {
      return Err(e);
    }
  } catch (e: any) {
    console.log(e);
    return Err(e);
  }

  callback.onStart(total);

  for (const episodeId of chapterIds) {
    try {
      console.log(`获取章节 ${fileName}/${episodeId}`);
      const textsSrc = await getNonArchivedChapter(fileName, episodeId);
      console.log(`翻译章节 ${fileName}/${episodeId}`);
      const textsDst = await translator.translate(textsSrc);
      console.log(`上传章节 ${fileName}/${episodeId}`);
      await postNonArchivedChapter(fileName, episodeId, version, textsDst);
      callback.onEpisodeTranslateSuccess();
    } catch (e) {
      console.log(e);
      callback.onEpisodeTranslateFailure();
    }
  }

  return Ok(undefined);
}

export default {
  list,
  getMetadata,
  postMetadata,
  patchMetadata,
  getMetadataFromBangumi,
  createUploadUrl,
  //
  listNonArchived,
  createNonArchivedUploadUrl,
  getNonArchivedState,
  update,
};
