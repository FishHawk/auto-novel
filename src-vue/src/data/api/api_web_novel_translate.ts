import { TranslatorId, createTranslator } from '@/data/translator/translator';
import { TranslatorAdapter } from '@/data/translator/adapter';

import api from './api';
import { Result, Ok, Err } from './result';

interface MetadataDto {
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
  translatorId: TranslatorId,
  startIndex: number,
  endIndex: number
): Promise<MetadataDto> {
  const url = `novel/${providerId}/${novelId}/translate/${translatorId}/metadata`;
  return api.get(url, { searchParams: { startIndex, endIndex } }).json();
}

interface MetadataUpdateBody {
  title?: string;
  introduction?: string;
  toc: { [key: string]: string };
}

async function postMetadata(
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  body: MetadataUpdateBody
): Promise<string> {
  const url = `novel/${providerId}/${novelId}/translate/${translatorId}/metadata`;
  return api.post(url, { json: body }).text();
}

interface ChapterToTranslateDto {
  glossary: { [key: string]: string };
  paragraphsJp: string[];
}

async function getChapter(
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  chapterId: string
): Promise<ChapterToTranslateDto> {
  const url = `novel/${providerId}/${novelId}/translate/${translatorId}/chapter/${chapterId}`;
  return api.get(url).json();
}

interface ChapterUpdateBody {
  glossaryUuid: string | undefined;
  paragraphsZh: string[];
}

async function postChapter(
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  chapterId: string,
  body: ChapterUpdateBody
): Promise<number> {
  const url = `novel/${providerId}/${novelId}/translate/${translatorId}/chapter/${chapterId}`;
  return api.post(url, { json: body }).json();
}

interface ChapterUpdatePartlyBody {
  glossaryUuid: string | undefined;
  paragraphsZh: { [key: number]: string };
}

async function putChapter(
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  chapterId: string,
  body: ChapterUpdatePartlyBody
): Promise<number> {
  const url = `novel/${providerId}/${novelId}/translate/${translatorId}/chapter/${chapterId}`;
  return api.put(url, { json: body }).json();
}

function encodeMetadataToTranslate(metadata: MetadataDto): string[] {
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
  metadata: MetadataDto,
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
  onChapterTranslateSuccess: (state: number) => void;
  onChapterTranslateFailure: () => void;
}

export async function translate(
  providerId: string,
  novelId: string,
  translatorId: TranslatorId,
  startIndex: number,
  endIndex: number,
  callback: UpdateCallback
): Promise<Result<undefined, any>> {
  let metadata: MetadataDto;
  let translator: TranslatorAdapter | undefined = undefined;
  try {
    console.log(`获取元数据 ${providerId}/${novelId}`);
    metadata = await getMetadata(
      providerId,
      novelId,
      translatorId,
      startIndex,
      endIndex
    );

    try {
      translator = await createTranslator(translatorId, metadata.glossary);
    } catch (e: any) {
      return Err(e);
    }

    const textsSrc = encodeMetadataToTranslate(metadata);
    if (textsSrc.length > 0) {
      console.log(`翻译元数据 ${providerId}/${novelId}`);
      const textsDst = await translator.translate(textsSrc);

      console.log(`上传元数据 ${providerId}/${novelId}`);
      const metadataTranslated = decodeAsMetadataTranslated(metadata, textsDst);
      await postMetadata(providerId, novelId, translatorId, metadataTranslated);
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
      const chapter = await getChapter(
        providerId,
        novelId,
        translatorId,
        chapterId
      );

      const textsSrc = chapter.paragraphsJp;
      console.log(`翻译章节 ${providerId}/${novelId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);

      console.log(`上传章节 ${providerId}/${novelId}/${chapterId}`);
      const state = await postChapter(
        providerId,
        novelId,
        translatorId,
        chapterId,
        {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh: textsDst,
        }
      );
      callback.onChapterTranslateSuccess(state);
    } catch (e) {
      console.log(e);
      callback.onChapterTranslateFailure();
    }
  }

  for (const chapterId of metadata.expiredChapterIds) {
    try {
      console.log(`获取章节 ${providerId}/${novelId}/${chapterId}`);
      const chapter = await getChapter(
        providerId,
        novelId,
        translatorId,
        chapterId
      );
      const expiredParagraphs = getExpiredParagraphs(
        chapter,
        metadata.glossary
      );

      const textsSrc = expiredParagraphs.map((it) => it.text);
      const paragraphsZh: { [key: number]: string } = {};

      console.log(`翻译章节 ${providerId}/${novelId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);
      expiredParagraphs.forEach((it, index) => {
        paragraphsZh[it.index] = textsDst[index];
      });

      console.log(`上传章节 ${providerId}/${novelId}/${chapterId}`);
      const state = await putChapter(
        providerId,
        novelId,
        translatorId,
        chapterId,
        {
          glossaryUuid: metadata.glossaryUuid,
          paragraphsZh,
        }
      );
      callback.onChapterTranslateSuccess(state);
    } catch (e) {
      console.log(e);
      callback.onChapterTranslateFailure();
    }
  }

  return Ok(undefined);
}
