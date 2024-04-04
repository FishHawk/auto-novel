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
  useLlamaApi?: boolean;
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
}
