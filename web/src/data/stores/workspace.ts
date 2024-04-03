import { lazyUseLocalStorage } from '@/util/storage';
import { defineStore } from 'pinia';

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

export const isTranslateJobFinished = (job: TranslateJobRecord) =>
  job.progress !== undefined && job.progress.finished >= job.progress.total;

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

interface Workspace<T> {
  workers: T[];
  jobs: TranslateJob[];
  // 为了兼容性，仍使用 uncompletedJobs
  uncompletedJobs: TranslateJobRecord[];
}
const factory = <W extends GptWorker | SakuraWorker>(
  key: string,
  workers: W[]
) => {
  const lazyStorage = lazyUseLocalStorage<Workspace<W>>(key, {
    workers,
    jobs: [],
    uncompletedJobs: [],
  });
};

const useWorkspaceStoreFactory = <W extends GptWorker | SakuraWorker>(
  id: string,
  workers: W[]
) =>
  defineStore(id, {
    state: () =>
      <Workspace<W>>{
        workers,
        jobs: [],
        uncompletedJobs: [],
      },
    actions: {
      addWorker(worker: W) {
        this.workers.push(worker as any);
      },
      deleteWorker(id: string) {
        this.workers = this.workers.filter((w) => w.id !== id);
      },

      addJob(job: TranslateJob) {
        const conflictJob = this.jobs.find((it) => it.task === job.task);
        if (conflictJob !== undefined) {
          return false;
        } else {
          this.jobs.push(job);
          return true;
        }
      },
      deleteJob(task: string) {
        this.jobs = this.jobs.filter((j) => j.task !== task);
      },
      topJob(job: TranslateJob) {
        this.jobs.sort((a, b) => {
          return a.task == job.task ? -1 : b.task == job.task ? 1 : 0;
        });
      },
      bottomJob(job: TranslateJob) {
        this.jobs.sort((a, b) => {
          return a.task == job.task ? 1 : b.task == job.task ? -1 : 0;
        });
      },

      addJobRecord(job: TranslateJobRecord) {
        this.deleteJobRecord(job);
        this.uncompletedJobs.push(job);
      },
      deleteJobRecord(job: TranslateJobRecord) {
        this.uncompletedJobs = this.uncompletedJobs.filter(
          (j) => j.task !== job.task
        );
      },
      retryJobRecord(job: TranslateJobRecord) {
        this.addJob({
          task: job.task,
          description: job.description,
          createAt: Date.now(),
        });
        this.deleteJobRecord(job);
      },
      retryAllJobRecords() {
        const newArray: TranslateJobRecord[] = [];
        for (const job of this.uncompletedJobs) {
          if (isTranslateJobFinished(job)) {
            newArray.push(job);
          } else {
            this.addJob({
              task: job.task,
              description: job.description,
              createAt: Date.now(),
            });
          }
        }
        this.uncompletedJobs = newArray;
      },
      deleteAllJobRecords() {
        this.uncompletedJobs = [];
      },
    },
    persist: true,
  });

export const useGptWorkspaceStore = useWorkspaceStoreFactory<GptWorker>(
  'gpt-workspace',
  []
);

export const useSakuraWorkspaceStore = useWorkspaceStoreFactory<SakuraWorker>(
  'sakura-workspace',
  [
    { id: '本机', endpoint: 'http://127.0.0.1:8080', useLlamaApi: true },
    { id: 'AutoDL', endpoint: 'http://127.0.0.1:6006', useLlamaApi: true },
  ]
);

export type WorkspaceStore =
  | ReturnType<typeof useGptWorkspaceStore>
  | ReturnType<typeof useSakuraWorkspaceStore>;

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

export const buildWebTranslateTask = (
  providerId: string,
  novelId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
    toc: boolean;
  }
) => `web/${providerId}/${novelId}` + buildTaskQueryString(params);

export const buildWenkuTranslateTask = (
  novelId: string,
  volumeId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
  }
) => `wenku/${novelId}/${volumeId}` + buildTaskQueryString(params);

export const buildPersonalTranslateTask = (
  volumeId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
  }
) => `personal/${volumeId}` + buildTaskQueryString(params);

export const migrateGptWorkspace = (
  workspace: ReturnType<typeof useGptWorkspaceStore>
) => {
  // 2024-3-8
  workspace.workers.forEach((it) => {
    if (it.endpoint.length === 0) {
      if (it.type === 'web') {
        it.endpoint = 'https://chat.openai.com/backend-api';
      } else {
        it.endpoint = 'https://api.openai.com';
      }
    }
    if (it.type === 'web') {
      it.model = 'text-davinci-002-render-sha';
    } else {
      if (it.model === undefined || it.model === 'gpt-3.5') {
        it.model = 'gpt-3.5-turbo';
      }
    }
  });
};
