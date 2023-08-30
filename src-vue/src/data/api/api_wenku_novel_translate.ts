import { Options } from 'ky';

import { Translator } from '@/data/translator/base';
import {
  TranslatorConfig,
  TranslatorId,
  createTranslator,
} from '@/data/translator/translator';
import { useAuthInfoStore } from '@/data/stores/authInfo';

import api from './api';
import { Ok } from './result';

interface TranslateTaskDto {
  glossaryUuid?: string;
  glossary: { [key: string]: string };
  untranslatedChapters: string[];
  expiredChapters: string;
}

function getTranslateTask(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  token: string | undefined
): Promise<TranslateTaskDto> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}`;
  return api.get(url, options).json();
}

interface ChapterToTranslateDto {
  glossary: { [key: string]: string };
  paragraphsJp: string[];
}

function getChapterToTranslate(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string,
  token: string | undefined
): Promise<ChapterToTranslateDto> {
  const options: Options = {};
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.get(url, options).json();
}

interface ChapterUpdateBody {
  glossaryUuid: string | undefined;
  paragraphsZh: string[];
}

function postTranslateChapter(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string,
  body: ChapterUpdateBody,
  token: string | undefined
): Promise<number> {
  const options: Options = { json: body };
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.post(url, options).json();
}

interface ChapterUpdatePartlyBody {
  glossaryUuid: string | undefined;
  paragraphsZh: { [key: number]: string };
}

async function putTranslateChapter(
  novelId: string,
  translatorId: TranslatorId,
  volumeId: string,
  chapterId: string,
  body: ChapterUpdatePartlyBody,
  token: string | undefined
): Promise<number> {
  const options: Options = { json: body };
  if (token) {
    options.headers = { Authorization: 'Bearer ' + token };
  }
  const url = `wenku/${novelId}/translate/${translatorId}/${volumeId}/${chapterId}`;
  return api.put(url, options).json();
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

  let task: TranslateTaskDto;
  try {
    callback.log(`获取未翻译章节 ${volumeId}`);
    task = await getTranslateTask(novelId, translatorId, volumeId, token);
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

  callback.onStart(
    task.untranslatedChapters.length + task.expiredChapters.length
  );
  if (task.untranslatedChapters.length + task.expiredChapters.length) {
    callback.log(`没有需要更新的章节`);
  }

  for (const chapterId of task.untranslatedChapters) {
    try {
      callback.log(`\n获取章节 ${volumeId}/${chapterId}`);
      const chapter = await getChapterToTranslate(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        token
      );

      const textsSrc = chapter.paragraphsJp;
      callback.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await postTranslateChapter(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        {
          glossaryUuid: task.glossaryUuid,
          paragraphsZh: textsDst,
        },
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

  for (const chapterId of task.expiredChapters) {
    try {
      callback.log(`\n获取章节 ${volumeId}/${chapterId}`);
      const chapter = await getChapterToTranslate(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        token
      );
      const expiredParagraphs = getExpiredParagraphs(chapter, task.glossary);

      const textsSrc = expiredParagraphs.map((it) => it.text);
      const paragraphsZh: { [key: number]: string } = {};

      callback.log(`翻译章节 ${volumeId}/${chapterId}`);
      const textsDst = await translator.translate(textsSrc);
      expiredParagraphs.forEach((it, index) => {
        paragraphsZh[it.index] = textsDst[index];
      });

      callback.log(`上传章节 ${volumeId}/${chapterId}`);
      const state = await putTranslateChapter(
        novelId,
        translatorId,
        volumeId,
        chapterId,
        {
          glossaryUuid: task.glossaryUuid,
          paragraphsZh,
        },
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
