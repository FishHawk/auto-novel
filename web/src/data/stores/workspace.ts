import { useLocalStorage } from '@vueuse/core';
import { defineStore } from 'pinia';

export interface TranslateJob {
  task: string;
  description: string;
  createAt: number;
}

export type UncompletedTranslateJob = TranslateJob & {
  progress: {
    finished: number;
    error: number;
    total: number;
  };
};

export interface SakuraWorker {
  id: string;
  endpoint: string;
  useLlamaApi?: boolean;
}

export interface SakuraWorkspace {
  workers: SakuraWorker[];
  jobs: TranslateJob[];
  uncompletedJobs: UncompletedTranslateJob[];
}

export const useSakuraWorkspaceStore = defineStore('sakura-workspace', {
  state: () =>
    <SakuraWorkspace>{
      workers: [
        { id: '本机', endpoint: 'http://127.0.0.1:8080', useLlamaApi: true },
        { id: 'AutoDL', endpoint: 'http://127.0.0.1:6006', useLlamaApi: true },
      ],
      jobs: [],
      uncompletedJobs: [],
    },
  actions: {
    addWorker(worker: SakuraWorker) {
      this.workers.push(worker);
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

    addUncompletedJob(job: UncompletedTranslateJob) {
      this.deleteUncompletedJob(job);
      this.uncompletedJobs.push(job);
    },
    deleteUncompletedJob(job: UncompletedTranslateJob) {
      this.uncompletedJobs = this.uncompletedJobs.filter(
        (j) => j.task !== job.task
      );
    },
    retryUncompletedJob(job: UncompletedTranslateJob) {
      this.addJob({
        task: job.task,
        description: job.description,
        createAt: Date.now(),
      });
      this.deleteUncompletedJob(job);
    },
    retryAllUncompletedJobs() {
      for (const job of this.uncompletedJobs) {
        this.addJob({
          task: job.task,
          description: job.description,
          createAt: Date.now(),
        });
      }
      this.deleteAllUncompletedJobs();
    },
    deleteAllUncompletedJobs() {
      this.uncompletedJobs = [];
    },
  },
  persist: true,
});

export interface GptWorker {
  id: string;
  endpoint: string;
  type: 'web' | 'api';
  model?: 'gpt-3.5' | 'gpt-4';
  key: string;
}

export interface GptWorkspace {
  workers: GptWorker[];
  jobs: TranslateJob[];
  uncompletedJobs: UncompletedTranslateJob[];
}

export const useGptWorkspaceStore = defineStore('gpt-workspace', {
  state: () =>
    <GptWorkspace>{
      workers: [],
      jobs: [],
      uncompletedJobs: [],
    },
  actions: {
    addWorker(worker: GptWorker) {
      this.workers.push(worker);
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

    addUncompletedJob(job: UncompletedTranslateJob) {
      this.deleteUncompletedJob(job);
      this.uncompletedJobs.push(job);
    },
    deleteUncompletedJob(job: UncompletedTranslateJob) {
      this.uncompletedJobs = this.uncompletedJobs.filter(
        (j) => j.task !== job.task
      );
    },
    retryUncompletedJob(job: UncompletedTranslateJob) {
      this.addJob({
        task: job.task,
        description: job.description,
        createAt: Date.now(),
      });
      this.deleteUncompletedJob(job);
    },
    retryAllUncompletedJobs() {
      for (const job of this.uncompletedJobs) {
        this.addJob({
          task: job.task,
          description: job.description,
          createAt: Date.now(),
        });
      }
      this.deleteAllUncompletedJobs();
    },
    deleteAllUncompletedJobs() {
      this.uncompletedJobs = [];
    },
  },
  persist: true,
});

const buildTaskQueryString = ({
  start,
  end,
  expire,
}: {
  start: number;
  end: number;
  expire: boolean;
}) => {
  const searchParamsInit: { [key: string]: string } = {};
  if (start > 0) searchParamsInit['start'] = start.toString();
  if (end < 65535) searchParamsInit['end'] = end.toString();
  if (expire) searchParamsInit['expire'] = expire.toString();
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

export const buildPersonalLegacyTranslateTask = (
  volumeId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
  }
) => `personal/${volumeId}` + buildTaskQueryString(params);

export const buildPersonalTranslateTask = (
  volumeId: string,
  params: {
    start: number;
    end: number;
    expire: boolean;
  }
) => `personal2/${volumeId}` + buildTaskQueryString(params);
