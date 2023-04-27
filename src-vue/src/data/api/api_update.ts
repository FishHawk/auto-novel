import api from './api';
import { Result, Ok, Err } from './result';
import { TranslatorAdapter } from '../translator/adapter';
import { BaiduTranslator } from '../translator/baidu';
import { YoudaoTranslator } from '../translator/youdao';

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
  version: 'jp' | 'baidu' | 'youdao',
  startIndex: number,
  endIndex: number
): Promise<MetadataToTranslateDto> {
  return api
    .get(`update/metadata/${providerId}/${bookId}`, {
      searchParams: { version, startIndex, endIndex },
    })
    .json();
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
  episodeId: string,
  version: 'jp' | 'baidu' | 'youdao'
): Promise<EpisodeToTranslateDto> {
  return api
    .get(`update/episode/${providerId}/${bookId}/${episodeId}`, {
      searchParams: { version },
    })
    .json();
}

interface EpisodeUpdateBody {
  glossaryUuid: string | undefined;
  paragraphsZh: string[];
}

async function postEpisode(
  providerId: string,
  bookId: string,
  episodeId: string,
  version: 'baidu' | 'youdao',
  body: EpisodeUpdateBody
): Promise<string> {
  return api
    .post(`update/episode/${providerId}/${bookId}/${episodeId}`, {
      searchParams: { version },
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
  version: 'baidu' | 'youdao',
  body: EpisodeUpdatePartlyBody
): Promise<string> {
  return api
    .put(`update/episode/${providerId}/${bookId}/${episodeId}`, {
      searchParams: { version },
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

interface UpdateCallback {
  onStart: (total: number) => void;
  onEpisodeTranslateSuccess: () => void;
  onEpisodeTranslateFailure: () => void;
}

export async function update(
  version: 'jp' | 'baidu' | 'youdao',
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number,
  callback: UpdateCallback
): Promise<Result<undefined, any>> {
  let metadata: MetadataToTranslateDto;
  let translator: TranslatorAdapter | undefined = undefined;
  try {
    console.log(`获取元数据 ${providerId}/${bookId}`);
    metadata = await getMetadata(
      providerId,
      bookId,
      version,
      startIndex,
      endIndex
    );

    if (version !== 'jp') {
      try {
        if (version === 'baidu') {
          translator = new TranslatorAdapter(
            await BaiduTranslator.create(),
            metadata.glossary
          );
        } else {
          translator = new TranslatorAdapter(
            await YoudaoTranslator.create(),
            metadata.glossary
          );
        }
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

  callback.onStart(
    metadata.untranslatedEpisodeIds.length + metadata.expiredEpisodeIds.length
  );

  for (const episodeId of metadata.untranslatedEpisodeIds) {
    try {
      console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
      const episode = await getEpisode(providerId, bookId, episodeId, version);

      const textsSrc = episode.paragraphsJp;
      if (version !== 'jp' && translator) {
        console.log(`翻译章节 ${providerId}/${bookId}/${episodeId}`);
        const textsDst = await translator.translate(textsSrc);

        console.log(`上传章节 ${providerId}/${bookId}/${episodeId}`);
        await postEpisode(providerId, bookId, episodeId, version, {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh: textsDst,
        });
      }

      callback.onEpisodeTranslateSuccess();
    } catch (e) {
      console.log(e);
      callback.onEpisodeTranslateFailure();
    }
  }

  for (const episodeId of metadata.expiredEpisodeIds) {
    try {
      console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
      const episode = await getEpisode(providerId, bookId, episodeId, version);
      const expiredParagraphs = getExpiredParagraphs(
        episode,
        metadata.glossary
      );

      const textsSrc = expiredParagraphs.map((it) => it.text);
      const paragraphsZh: { [key: number]: string } = {};
      if (version !== 'jp' && translator) {
        console.log(`翻译章节 ${providerId}/${bookId}/${episodeId}`);
        const textsDst = await translator.translate(textsSrc);
        expiredParagraphs.forEach((it, index) => {
          paragraphsZh[it.index] = textsDst[index];
        });

        console.log(`上传章节 ${providerId}/${bookId}/${episodeId}`);
        await putEpisode(providerId, bookId, episodeId, version, {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh,
        });
      }
      callback.onEpisodeTranslateSuccess();
    } catch (e) {
      console.log(e);
      callback.onEpisodeTranslateFailure();
    }
  }

  return Ok(undefined);
}
