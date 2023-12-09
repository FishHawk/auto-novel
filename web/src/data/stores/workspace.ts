import { defineStore } from 'pinia';

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

export type UncompletedTranslateJob = TranslateJob & {
  progress: {
    finished: number;
    error: number;
    total: number;
  };
};

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
      this.deleteUncompletedJob(job.task);
      this.uncompletedJobs.push(job);
    },
    deleteUncompletedJob(task: string) {
      this.uncompletedJobs = this.uncompletedJobs.filter(
        (j) => j.task !== task
      );
    },
    clearUncompletedJobs() {
      this.uncompletedJobs = [];
    },
  },
  persist: true,
});
