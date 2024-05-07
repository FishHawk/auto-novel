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

export type PersonalTranslateTaskDesc = {
  type: 'personal';
  volumeId: string;
};

export type TranslateTaskDesc =
  | WebTranslateTaskDesc
  | WenkuTranslateTaskDesc
  | PersonalTranslateTaskDesc;

export type TranslateTaskParams = {
  translateExpireChapter: boolean;
  overriteToc: boolean;
  syncFromProvider: boolean;
  startIndex: number;
  endIndex: number;
};

export type TranslateTaskCallback = {
  onStart: (total: number) => void;
  onChapterSuccess: (state: { jp?: number; zh?: number }) => void;
  onChapterFailure: () => void;
  log: (message: string, detail?: string[]) => void;
};

export type TranslatorDesc =
  | { id: 'baidu' }
  | { id: 'youdao' }
  | {
      id: 'gpt';
      type: 'web' | 'api';
      model: string;
      endpoint: string;
      key: string;
    }
  | { id: 'sakura'; endpoint: string };

type TranslateTaskDescriptor = string;

export namespace TranslateTaskDescriptor {
  const buildTaskQueryString = ({
    start,
    end,
    expire,
    toc,
  }: {
    start: number;
    end: number;
    expire: boolean;
    toc?: boolean;
  }) => {
    const searchParamsInit: { [key: string]: string } = {};
    if (start > 0) searchParamsInit['start'] = start.toString();
    if (end < 65535) searchParamsInit['end'] = end.toString();
    if (expire) searchParamsInit['expire'] = expire.toString();
    if (toc) searchParamsInit['toc'] = expire.toString();
    const searchParams = new URLSearchParams(searchParamsInit).toString();
    return searchParams ? `?${searchParams}` : '';
  };

  export const web = (
    providerId: string,
    novelId: string,
    params: {
      start: number;
      end: number;
      expire: boolean;
      toc: boolean;
    }
  ) => `web/${providerId}/${novelId}` + buildTaskQueryString(params);

  export const wenku = (
    novelId: string,
    volumeId: string,
    params: {
      start: number;
      end: number;
      expire: boolean;
    }
  ) => `wenku/${novelId}/${volumeId}` + buildTaskQueryString(params);

  export const workspace = (
    volumeId: string,
    params: {
      start: number;
      end: number;
      expire: boolean;
    }
  ) => `personal/${volumeId}` + buildTaskQueryString(params);

  export const parse = (task: string) => {
    const [taskString, queryString] = task.split('?');
    const { start, end, expire, toc } = Object.fromEntries(
      new URLSearchParams(queryString) as any
    );

    let desc: TranslateTaskDesc;
    if (taskString.startsWith('web/')) {
      const [type, providerId, novelId] = taskString.split('/');
      desc = { type: type as any, providerId, novelId };
    } else if (taskString.startsWith('wenku/')) {
      const [type, novelId, volumeId] = taskString.split('/');
      desc = { type: type as any, novelId, volumeId };
    } else if (
      taskString.startsWith('personal/') ||
      taskString.startsWith('personal2/')
    ) {
      const [_type, volumeId] = taskString.split('/');
      desc = { type: 'personal', volumeId };
    } else {
      throw 'quit';
    }

    const parseIntWithDefault = (str: string, defaultValue: number) => {
      const num = parseInt(str, 10);
      return isNaN(num) ? defaultValue : num;
    };

    const params: TranslateTaskParams = {
      translateExpireChapter: expire === 'true',
      overriteToc: toc === 'true',
      syncFromProvider: false,
      startIndex: parseIntWithDefault(start, 0),
      endIndex: parseIntWithDefault(end, 65535),
    };

    return { desc, params };
  };

  export const parseUrl = (task: string) => {
    const { desc } = parse(task);
    if (desc.type === 'web') {
      return `/novel/${desc.providerId}/${desc.novelId}`;
    } else if (desc.type === 'wenku') {
      return `/wenku/${desc.novelId}`;
    } else {
      return undefined;
    }
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
  oldGlossary: Glossary;
}
