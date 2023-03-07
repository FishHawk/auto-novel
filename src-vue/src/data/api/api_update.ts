import { SearchParamsOption } from 'ky';
import { Ref } from 'vue';

import api from './api';
import { UpdateProgress } from './progress';
import { Result, Ok, Err } from './result';
import { BaiduWebTranslator } from '../translator/baidu-web';
import { Translator } from '../translator/base';

interface MetadataToTranslateDto {
  title?: string;
  introduction?: string;
  toc: string[];
  glossaryUuid?: string;
  glossary: { [key: string]: string };
  untranslatedEpisodeIds: string[];
  expiredEpisodeIds: string[];
}

async function getMetadata(
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number
): Promise<MetadataToTranslateDto> {
  return api
    .get(`update/metadata/${providerId}/${bookId}`, {
      searchParams: { startIndex, endIndex },
    })
    .json<MetadataToTranslateDto>();
}

interface MetadataUpdateBody {
  title?: string;
  introduction?: string;
  toc: { [key: string]: string };
}

async function postMetadata(
  providerId: string,
  bookId: string,
  body: MetadataUpdateBody
): Promise<string> {
  return api
    .post(`update/metadata/${providerId}/${bookId}`, {
      json: body,
    })
    .text();
}

interface EpisodeToTranslateDto {
  glossary: { [key: string]: string };
  paragraphsJp: string[];
}

async function getEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<EpisodeToTranslateDto> {
  return api
    .get(`update/episode/${providerId}/${bookId}/${episodeId}`)
    .json<EpisodeToTranslateDto>();
}

interface EpisodeUpdateBody {
  glossaryUuid: string | undefined;
  paragraphsZh: string[];
}

async function postEpisode(
  providerId: string,
  bookId: string,
  episodeId: string,
  body: EpisodeUpdateBody
): Promise<string> {
  return api
    .post(`update/episode/${providerId}/${bookId}/${episodeId}`, {
      json: body,
    })
    .text();
}

interface EpisodeUpdatePartlyBody {
  glossaryUuid: string | undefined;
  paragraphsZh: { [key: number]: string };
}

async function putEpisode(
  providerId: string,
  bookId: string,
  episodeId: string,
  body: EpisodeUpdatePartlyBody
): Promise<string> {
  return api
    .put(`update/episode/${providerId}/${bookId}/${episodeId}`, {
      json: body,
    })
    .text();
}

function encodeMetadataToTranslate(metadata: MetadataToTranslateDto): string[] {
  const query = [];
  if (metadata.title) {
    query.push(metadata.title);
  }
  if (metadata.introduction) {
    query.push(metadata.introduction);
  }
  query.push(...metadata.toc);
  return query;
}

function decodeAsMetadataTranslated(
  metadata: MetadataToTranslateDto,
  translated: string[]
): MetadataUpdateBody {
  const obj: MetadataUpdateBody = { toc: {} };
  if (metadata.title) {
    obj.title = translated.shift();
  }
  if (metadata.introduction) {
    obj.introduction = translated.shift();
  }
  for (const textJp of metadata.toc) {
    obj.toc[textJp] = translated.shift()!!;
  }
  return obj;
}

function getExpiredParagraphs(
  episode: EpisodeToTranslateDto,
  glossary: { [key: string]: string }
) {
  const changedWords: string[] = [];
  for (const word in glossary) {
    if (episode.glossary[word] != glossary[word]) {
      changedWords.push(word);
    }
  }
  for (const word in episode.glossary) {
    if (!(word in glossary)) {
      changedWords.push(word);
    }
  }
  return episode.paragraphsJp
    .map((text, index) => ({ text, index }))
    .filter((it) => {
      for (const word of changedWords) {
        if (it.text.includes(word)) return true;
      }
      return false;
    });
}

export async function update(
  needTranslate: boolean,
  progress: Ref<UpdateProgress | undefined>,
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number,
  onStart: () => void
): Promise<Result<UpdateProgress, any>> {
  const name = needTranslate ? '更新中文' : '更新日文';
  let total = undefined;
  let finished = 0;
  let error = 0;
  progress.value = { name, total, finished, error };

  let metadata: MetadataToTranslateDto;
  let translator: Translator | undefined = undefined;
  try {
    console.log(`获取元数据 ${providerId}/${bookId}`);
    metadata = await getMetadata(providerId, bookId, startIndex, endIndex);

    if (needTranslate) {
      try {
        translator = await BaiduWebTranslator.createInstance(
          'jp',
          'zh',
          metadata.glossary
        );
      } catch (e: any) {
        return Err(e);
      }

      const textsSrc = encodeMetadataToTranslate(metadata);
      if (textsSrc.length > 0) {
        console.log(`翻译元数据 ${providerId}/${bookId}`);
        const textsDst = await translator.translate(textsSrc);

        console.log(`上传元数据 ${providerId}/${bookId}`);
        const metadataTranslated = decodeAsMetadataTranslated(
          metadata,
          textsDst
        );
        await postMetadata(providerId, bookId, metadataTranslated);
      }
    }
  } catch (e: any) {
    console.log(e);
    return Err(e);
  }

  total =
    metadata.untranslatedEpisodeIds.length + metadata.expiredEpisodeIds.length;
  progress.value = { name, total, finished, error };
  onStart();

  for (const episodeId of metadata.untranslatedEpisodeIds) {
    try {
      console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
      const episode = await getEpisode(providerId, bookId, episodeId);

      const textsSrc = episode.paragraphsJp;
      if (translator && textsSrc.length > 0) {
        console.log(`翻译章节 ${providerId}/${bookId}/${episodeId}`);
        const textsDst = await translator.translateWithGlossary(textsSrc);

        console.log(`上传章节 ${providerId}/${bookId}/${episodeId}`);
        await postEpisode(providerId, bookId, episodeId, {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh: textsDst,
        });
      }

      finished += 1;
    } catch (e) {
      console.log(e);
      error += 1;
    }

    progress.value = { name, total, finished, error };
  }

  for (const episodeId of metadata.expiredEpisodeIds) {
    try {
      console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
      const episode = await getEpisode(providerId, bookId, episodeId);
      const expiredParagraphs = getExpiredParagraphs(
        episode,
        metadata.glossary
      );

      const textsSrc = expiredParagraphs.map((it) => it.text);
      if (translator && textsSrc.length > 0) {
        console.log(`翻译章节 ${providerId}/${bookId}/${episodeId}`);
        const textsDst = await translator.translateWithGlossary(textsSrc);

        console.log(`上传章节 ${providerId}/${bookId}/${episodeId}`);
        const updatedParagraphs: { [key: number]: string } = {};
        expiredParagraphs.forEach((it, index) => {
          updatedParagraphs[it.index] = textsDst[index];
        });
        await putEpisode(providerId, bookId, episodeId, {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh: updatedParagraphs,
        });
      }

      finished += 1;
    } catch (e) {
      console.log(e);
      error += 1;
    }

    progress.value = { name, total, finished, error };
  }

  return Ok({ name, total, finished, error });
}
