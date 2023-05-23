import api from './api';
import { Err, Ok, Result } from './result';
import { TranslatorAdapter } from '../translator/adapter';
import { TranslatorId, createTranslator } from '../translator/translator';

function getUntranslatedChapter(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string
): Promise<string[]> {
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}`;
  return api.get(url).json();
}

function getChapterToTranslate(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string
): Promise<string[]> {
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.get(url).json();
}

function postTranslateChapter(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string,
  lines: string[]
): Promise<number> {
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.post(url, { json: lines }).json();
}

interface UpdateCallback {
  onStart: (total: number) => void;
  onChapterTranslateSuccess: (state: number) => void;
  onChapterTranslateFailure: () => void;
}

export async function translate(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  callback: UpdateCallback
): Promise<Result<undefined, any>> {
  let translator: TranslatorAdapter;
  try {
    translator = await createTranslator(translatorId, {});
  } catch (e: any) {
    return Err(e);
  }

  let chapterIds: string[];
  try {
    console.log(`获取未翻译章节 ${volumeId}`);
    chapterIds = await getUntranslatedChapter(novelId, translatorId, volumeId);
  } catch (e: any) {
    console.log(e);
    return Err(e);
  }

  callback.onStart(chapterIds.length);

  for (const chapterId of chapterIds) {
    try {
      console.log(`获取章节 ${volumeId}/${chapterId}`);
      const textsSrc = await getChapterToTranslate(
        novelId,
        translatorId,
        volumeId,
        chapterId
      );
      console.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);
      console.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await postTranslateChapter(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        textsDst
      );
      callback.onChapterTranslateSuccess(state);
    } catch (e) {
      console.log(e);
      callback.onChapterTranslateFailure();
    }
  }

  return Ok(undefined);
}
