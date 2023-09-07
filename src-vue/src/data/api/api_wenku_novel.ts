import ky from 'ky';

import { Translator } from '@/data/translator/base';
import {
  TranslatorConfig,
  TranslatorId,
  createTranslator,
} from '@/data/translator/translator';

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

const translate = async (
  {
    novelId,
    translatorId,
    volumeId,
    accessToken,
    translateExpireChapter,
  }: {
    novelId: string;
    translatorId: TranslatorId;
    volumeId: string;
    accessToken?: string;
    translateExpireChapter: boolean;
  },
  callback: {
    onStart: (total: number) => void;
    onChapterSuccess: (state: number) => void;
    onChapterFailure: () => void;
    log: (message: any) => void;
  }
) => {
  // Api
  interface TranslateTaskDto {
    glossaryUuid?: string;
    glossary: { [key: string]: string };
    untranslatedChapters: string[];
    expiredChapters: string;
  }
  const endpoint = `wenku/${novelId}/translate/${translatorId}/${volumeId}`;
  const getTranslateTask = () => api.get(endpoint).json<TranslateTaskDto>();

  const getChapterToTranslate = (chapterId: string) =>
    api.get(`${endpoint}/${chapterId}`).json<string[]>();

  const updateChapterTranslation = (
    chapterId: string,
    json: { glossaryUuid: string | undefined; paragraphsZh: string[] }
  ) => api.put(`${endpoint}/${chapterId}`, { json }).json<number>();

  // Task
  let task: TranslateTaskDto;
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    task = await getTranslateTask();
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  let translator: Translator;
  try {
    const config: TranslatorConfig = {
      log: (message) => callback.log('　　' + message),
    };
    if (translatorId === 'gpt') {
      if (!accessToken) {
        throw Error('GPT翻译需要输入Token');
      } else {
        config.accessToken = accessToken;
      }
    } else {
      config.glossary = task.glossary;
    }
    translator = await createTranslator(translatorId, config);
  } catch (e: any) {
    callback.log(`发生错误，无法创建翻译器：${e}`);
    return;
  }

  let chapters = translateExpireChapter
    ? task.untranslatedChapters.concat(task.expiredChapters)
    : task.untranslatedChapters;

  callback.onStart(chapters.length);
  if (chapters.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const chapterId of chapters) {
    try {
      callback.log(`\n获取章节 ${volumeId}/${chapterId}`);
      const textsJp = await getChapterToTranslate(chapterId);

      callback.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsZh = await translator.translate(textsJp);

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await updateChapterTranslation(chapterId, {
        glossaryUuid: task.glossaryUuid,
        paragraphsZh: textsZh,
      });
      callback.onChapterSuccess(state);
    } catch (e) {
      if (e === 'quit') {
        callback.log(`发生错误，结束翻译任务`);
        return;
      } else {
        callback.log(`发生错误，跳过这个章节：${e}`);
        callback.onChapterFailure();
      }
    }
  }

  return Ok(undefined);
};

export const ApiWenkuNovel = {
  list,
  listVolumesUser,
  listVolumesNonArchived,
  getMetadata,
  postMetadata,
  patchMetadata,
  updateGlossary,
  notifyUpdate,
  getMetadataFromBangumi,
  createVolumeZhUploadUrl,
  createVolumeJpUploadUrl,
  //
  translate,
  createFileUrl,
};
