import { Options } from 'ky';
import api from './api';
import { Err, Ok, Result } from './result';
import { Translator } from '../translator/base';
import {
  TranslatorConfig,
  TranslatorId,
  createTranslator,
} from '../translator/translator';
import { useAuthInfoStore } from '../stores/authInfo';

function getUntranslatedChapter(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  token: string | undefined
): Promise<string[]> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}`;
  return api.get(url, options).json();
}

function getChapterToTranslate(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string,
  token: string | undefined
): Promise<string[]> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.get(url, options).json();
}

function postTranslateChapter(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string,
  lines: string[],
  token: string | undefined
): Promise<number> {
  const options: Options = {
    json: lines,
  };
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.post(url, options).json();
}

interface UpdateCallback {
  onStart: (total: number) => void;
  onChapterSuccess: (state: number) => void;
  onChapterFailure: () => void;
  log: (message: any) => void;
}

export async function translate(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  accessToken: string | undefined,
  callback: UpdateCallback
) {
  const token = useAuthInfoStore().token;

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
    }
    translator = await createTranslator(translatorId, config);
  } catch (e: any) {
    callback.log(`发生错误，无法创建翻译器：${e}`);
    return;
  }

  let chapterIds: string[];
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    chapterIds = await getUntranslatedChapter(
      novelId,
      translatorId,
      volumeId,
      token
    );
  } catch (e: any) {
    callback.log(`发生错误，结束翻译任务：${e}`);
    return;
  }

  callback.onStart(chapterIds.length);
  if (chapterIds.length === 0) {
    callback.log(`没有需要更新的章节`);
  }

  for (const chapterId of chapterIds) {
    try {
      callback.log(`\n获取章节 ${volumeId}/${chapterId}`);
      const textsSrc = await getChapterToTranslate(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        token
      );
      callback.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);
      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await postTranslateChapter(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        textsDst,
        token
      );
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
}
