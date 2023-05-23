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
  untranslatedChapterIds: string[];
  expiredChapterIds: string[];
}

async function getMetadata(
  providerId: string,
  novelId: string,
  version: 'jp' | 'baidu' | 'youdao',
  startIndex: number,
  endIndex: number
): Promise<MetadataToTranslateDto> {
  return api
    .get(`update/metadata/${providerId}/${novelId}`, {
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
  novelId: string,
  body: MetadataUpdateBody
): Promise<string> {
  return api
    .post(`update/metadata/${providerId}/${novelId}`, {
      json: body,
    })
    .text();
}

interface ChapterToTranslateDto {
  glossary: { [key: string]: string };
  paragraphsJp: string[];
}

async function getChapter(
  providerId: string,
  novelId: string,
  chapterId: string,
  version: 'jp' | 'baidu' | 'youdao'
): Promise<ChapterToTranslateDto> {
  return api
    .get(`update/chapter/${providerId}/${novelId}/${chapterId}`, {
      searchParams: { version },
    })
    .json();
}

interface ChapterUpdateBody {
  glossaryUuid: string | undefined;
  paragraphsZh: string[];
}

async function postChapter(
  providerId: string,
  novelId: string,
  chapterId: string,
  version: 'baidu' | 'youdao',
  body: ChapterUpdateBody
): Promise<string> {
  return api
    .post(`update/chapter/${providerId}/${novelId}/${chapterId}`, {
      searchParams: { version },
      json: body,
    })
    .text();
}

interface ChapterUpdatePartlyBody {
  glossaryUuid: string | undefined;
  paragraphsZh: { [key: number]: string };
}

async function putChapter(
  providerId: string,
  novelId: string,
  chapterId: string,
  version: 'baidu' | 'youdao',
  body: ChapterUpdatePartlyBody
): Promise<string> {
  return api
    .put(`update/chapter/${providerId}/${novelId}/${chapterId}`, {
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
  chapter: ChapterToTranslateDto,
  glossary: { [key: string]: string }
) {
  const changedWords: string[] = [];
  for (const word in glossary) {
    if (chapter.glossary[word] != glossary[word]) {
      changedWords.push(word);
    }
  }
  for (const word in chapter.glossary) {
    if (!(word in glossary)) {
      changedWords.push(word);
    }
  }
  return chapter.paragraphsJp
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
  onChapterTranslateSuccess: () => void;
  onChapterTranslateFailure: () => void;
}

export async function update(
  version: 'jp' | 'baidu' | 'youdao',
  providerId: string,
  novelId: string,
  startIndex: number,
  endIndex: number,
  callback: UpdateCallback
): Promise<Result<undefined, any>> {
  let metadata: MetadataToTranslateDto;
  let translator: TranslatorAdapter | undefined = undefined;
  try {
    console.log(`获取元数据 ${providerId}/${novelId}`);
    metadata = await getMetadata(
      providerId,
      novelId,
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
        console.log(`翻译元数据 ${providerId}/${novelId}`);
        const textsDst = await translator.translate(textsSrc);

        console.log(`上传元数据 ${providerId}/${novelId}`);
        const metadataTranslated = decodeAsMetadataTranslated(
          metadata,
          textsDst
        );
        await postMetadata(providerId, novelId, metadataTranslated);
      }
    }
  } catch (e: any) {
    console.log(e);
    return Err(e);
  }

  callback.onStart(
    metadata.untranslatedChapterIds.length + metadata.expiredChapterIds.length
  );

  for (const chapterId of metadata.untranslatedChapterIds) {
    try {
      console.log(`获取章节 ${providerId}/${novelId}/${chapterId}`);
      const chapter = await getChapter(providerId, novelId, chapterId, version);

      const textsSrc = chapter.paragraphsJp;
      if (version !== 'jp' && translator) {
        console.log(`翻译章节 ${providerId}/${novelId}/${chapterId}`);
        const textsDst = await translator.translate(textsSrc);

        console.log(`上传章节 ${providerId}/${novelId}/${chapterId}`);
        await postChapter(providerId, novelId, chapterId, version, {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh: textsDst,
        });
      }

      callback.onChapterTranslateSuccess();
    } catch (e) {
      console.log(e);
      callback.onChapterTranslateFailure();
    }
  }

  for (const chapterId of metadata.expiredChapterIds) {
    try {
      console.log(`获取章节 ${providerId}/${novelId}/${chapterId}`);
      const chapter = await getChapter(providerId, novelId, chapterId, version);
      const expiredParagraphs = getExpiredParagraphs(
        chapter,
        metadata.glossary
      );

      const textsSrc = expiredParagraphs.map((it) => it.text);
      const paragraphsZh: { [key: number]: string } = {};
      if (version !== 'jp' && translator) {
        console.log(`翻译章节 ${providerId}/${novelId}/${chapterId}`);
        const textsDst = await translator.translate(textsSrc);
        expiredParagraphs.forEach((it, index) => {
          paragraphsZh[it.index] = textsDst[index];
        });

        console.log(`上传章节 ${providerId}/${novelId}/${chapterId}`);
        await putChapter(providerId, novelId, chapterId, version, {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh,
        });
      }
      callback.onChapterTranslateSuccess();
    } catch (e) {
      console.log(e);
      callback.onChapterTranslateFailure();
    }
  }

  return Ok(undefined);
}
