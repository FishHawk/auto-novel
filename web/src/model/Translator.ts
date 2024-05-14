import { Glossary } from './Glossary';

export type TranslatorId = 'sakura' | 'baidu' | 'youdao' | 'gpt';

export interface GptWorker {
  id: string;
  endpoint: string;
  type: 'web' | 'api';
  model: string;
  key: string;
}

export interface SakuraWorker {
  id: string;
  endpoint: string;
  segLength?: number;
  prevSegLength?: number;
}

export interface TranslateJob {
  task: string;
  description: string;
  createAt: number;
}

export type TranslateJobRecord = TranslateJob & {
  progress?: {
    finished: number;
    error: number;
    total: number;
  };
};

export namespace TranslateJob {
  export const isFinished = (job: TranslateJobRecord) =>
    job.progress !== undefined && job.progress.finished >= job.progress.total;
}

//
export type WebTranslateTaskDesc = {
  type: 'web';
  providerId: string;
  novelId: string;
};

export type WenkuTranslateTaskDesc = {
  type: 'wenku';
  novelId: string;
  volumeId: string;
};

export type LocalTranslateTaskDesc = {
  type: 'local';
  volumeId: string;
};

export type TranslateTaskDesc =
  | WebTranslateTaskDesc
  | WenkuTranslateTaskDesc
  | LocalTranslateTaskDesc;

export type TranslateTaskParams = {
  expire: boolean; // 是否翻译过期章节
  sync: boolean; // 是否与源站同步
  forceMetadata: boolean; // 强制重翻元数据
  forceSeg: boolean; // 强制重翻分段
  startIndex: number;
  endIndex: number;
};

export type TranslateTaskCallback = {
  onStart: (total: number) => void;
  onChapterSuccess: (state: { jp?: number; zh?: number }) => void;
  onChapterFailure: () => void;
  log: (message: string, detail?: string[]) => void;
};

type TranslateTaskDescriptor = string;

export namespace TranslateTaskDescriptor {
  const buildTaskQueryString = ({
    startIndex,
    endIndex,
    expire,
    sync,
    forceMetadata,
    forceSeg,
  }: TranslateTaskParams) => {
    const searchParamsInit: { [key: string]: string } = {
      startIndex: startIndex.toString(),
      endIndex: endIndex.toString(),
      expire: expire.toString(),
      sync: sync.toString(),
      forceMetadata: forceMetadata.toString(),
      forceSeg: forceSeg.toString(),
    };
    const searchParams = new URLSearchParams(searchParamsInit).toString();
    return searchParams ? `?${searchParams}` : '';
  };

  export const web = (
    providerId: string,
    novelId: string,
    params: TranslateTaskParams
  ) => `web/${providerId}/${novelId}` + buildTaskQueryString(params);

  export const wenku = (
    novelId: string,
    volumeId: string,
    params: TranslateTaskParams
  ) =>
    `wenku/${novelId}/${encodeURIComponent(volumeId)}` +
    buildTaskQueryString(params);

  export const workspace = (volumeId: string, params: TranslateTaskParams) =>
    `local/${encodeURIComponent(volumeId)}` + buildTaskQueryString(params);

  export const parse = (task: string) => {
    const [taskString, queryString] = task.split('?');

    let desc: TranslateTaskDesc;
    if (taskString.startsWith('web/')) {
      const [_, providerId, novelId] = taskString.split('/');
      desc = { type: 'web', providerId, novelId };
    } else if (taskString.startsWith('wenku/')) {
      const [_, novelId, volumeId] = taskString.split('/');
      desc = {
        type: 'wenku',
        novelId,
        volumeId: decodeURIComponent(volumeId),
      };
    } else if (
      taskString.startsWith('local/') ||
      taskString.startsWith('personal/') ||
      taskString.startsWith('personal2/')
    ) {
      const [_, volumeId] = taskString.split('/');
      desc = { type: 'local', volumeId: decodeURIComponent(volumeId) };
    } else {
      throw 'quit';
    }

    const query = Object.fromEntries(new URLSearchParams(queryString) as any);

    const queryBoolean = (name: string) => {
      return query[name] === 'true';
    };

    const queryInt = (name: string, defaultValue: number) => {
      const num = parseInt(query[name], 10);
      return isNaN(num) ? defaultValue : num;
    };

    const params: TranslateTaskParams = {
      expire: queryBoolean('expire'),
      sync: queryBoolean('sync'),
      forceMetadata: queryBoolean('forceMetadata'),
      forceSeg: queryBoolean('forceSeg'),
      startIndex: queryInt('startIndex', 0),
      endIndex: queryInt('endIndex', 65535),
    };

    return { desc, params };
  };
}

export interface WebTranslateTask {
  titleJp: string;
  titleZh?: string;
  introductionJp: string;
  introductionZh?: string;
  glossaryUuid: string;
  glossary: Glossary;
  toc: {
    chapterId?: string;
    titleJp: string;
    titleZh?: string;
    glossaryUuid?: string;
  }[];
}

export interface WebChapterTranslateTask {
  paragraphJp: string[];
  oldParagraphZh?: string[];
  glossaryId: string;
  glossary: Glossary;
  oldGlossaryId?: string;
  oldGlossary: Glossary;
}

export interface WenkuTranslateTask {
  glossaryId: string;
  toc: {
    chapterId: string;
    glossaryId?: string;
  }[];
}

export interface WenkuChapterTranslateTask {
  paragraphJp: string[];
  oldParagraphZh?: string[];
  glossaryId: string;
  glossary: Glossary;
  oldGlossaryId?: string;
  oldGlossary: Glossary;
}
