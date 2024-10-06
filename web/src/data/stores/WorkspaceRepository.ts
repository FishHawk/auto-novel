import { useLocalStorage } from '@vueuse/core';
import { KataKanaConfig, KataKanaInfo, VolumeHistory } from '@/model/KataKana';

import {
  GptWorker,
  SakuraWorker,
  TranslateJob,
  TranslateJobRecord,
} from '@/model/Translator';

interface Workspace<T> {
  workers: T[];
  jobs: TranslateJob[];
  // 为了兼容性，仍使用 uncompletedJobs
  uncompletedJobs: TranslateJobRecord[];
}

const createWorkspaceRepository = <W extends GptWorker | SakuraWorker>(
  id: string,
  workers: W[],
  migrate?: (ref: Ref<Workspace<W>>) => void,
) => {
  const ref = useLocalStorage<Workspace<W>>(id, {
    workers,
    jobs: [],
    uncompletedJobs: [],
  });

  if (migrate) {
    migrate(ref);
  }

  const addWorker = (worker: W) => {
    ref.value.workers.push(worker as any);
  };
  const deleteWorker = (id: string) => {
    ref.value.workers = ref.value.workers.filter((w) => w.id !== id);
  };

  const addJob = (job: TranslateJob) => {
    const conflictJob = ref.value.jobs.find((it) => it.task === job.task);
    if (conflictJob !== undefined) {
      // probe
      console.log(conflictJob);
      console.log(ref.value);
      return false;
    } else {
      ref.value.jobs.push(job);
      return true;
    }
  };
  const deleteJob = (task: string) => {
    ref.value.jobs = ref.value.jobs.filter((j) => j.task !== task);
  };
  const topJob = (job: TranslateJob) => {
    ref.value.jobs.sort((a, b) => {
      return a.task == job.task ? -1 : b.task == job.task ? 1 : 0;
    });
  };
  const bottomJob = (job: TranslateJob) => {
    ref.value.jobs.sort((a, b) => {
      return a.task == job.task ? 1 : b.task == job.task ? -1 : 0;
    });
  };

  const addJobRecord = (job: TranslateJobRecord) => {
    deleteJobRecord(job);
    ref.value.uncompletedJobs.push(job);
  };
  const deleteJobRecord = (job: TranslateJobRecord) => {
    ref.value.uncompletedJobs = ref.value.uncompletedJobs.filter(
      (j) => j.task !== job.task,
    );
  };
  const retryJobRecord = (job: TranslateJobRecord) => {
    addJob({
      task: job.task,
      description: job.description,
      createAt: Date.now(),
    });
    deleteJobRecord(job);
  };
  const retryAllJobRecords = () => {
    const newArray: TranslateJobRecord[] = [];
    for (const job of ref.value.uncompletedJobs) {
      if (TranslateJob.isFinished(job)) {
        newArray.push(job);
      } else {
        addJob({
          task: job.task,
          description: job.description,
          createAt: Date.now(),
        });
      }
    }
    ref.value.uncompletedJobs = newArray;
  };
  const deleteAllJobRecords = () => {
    ref.value.uncompletedJobs = [];
  };

  return {
    ref,
    //
    addWorker,
    deleteWorker,
    //
    addJob,
    deleteJob,
    topJob,
    bottomJob,
    //
    addJobRecord,
    deleteJobRecord,
    retryJobRecord,
    retryAllJobRecords,
    deleteAllJobRecords,
  };
};

export const createGptWorkspaceRepository = () =>
  createWorkspaceRepository<GptWorker>('gpt-workspace', [], (workspace) => {
    // 2024-3-8
    workspace.value.workers.forEach((it) => {
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
  });

export const createSakuraWorkspaceRepository = () =>
  createWorkspaceRepository<SakuraWorker>(
    'sakura-workspace',
    [
      { id: '本机', endpoint: 'http://127.0.0.1:8080' },
      { id: 'AutoDL', endpoint: 'http://127.0.0.1:6006' },
    ],
    (workspace) => {
      // 2024-5-14
      workspace.value.workers.forEach((it: any) => {
        it.testContext = undefined;
        if (typeof it.testSegLength === 'number') {
          it.segLength = it.testSegLength;
          it.testSegLength = undefined;
        }
      });
    },
  );

export const createKataKanaWorkSpaceRepository = (
  migrate?: (ref: KataKanaConfig) => void,
) => {
  const ref = useLocalStorage<KataKanaConfig>('katakana-workspace', {
    mode: 'traditional',
    aiTranslationType: 'openai',
    api_key: 'sk-no-key-required',
    base_url: 'http://127.0.0.1:8080',
    model_name: 'glm-4-9b-chat',
    max_workers: 4,
    request_timeout: 120,
    translate_surface_mode: 1,
    translate_context_mode: 1,
    history: [],
  });
  if (migrate) {
    migrate(ref.value);
  }

  const addHistory = (volume: VolumeHistory) => {
    ref.value.history.push(volume);
  };

  const deleteHistory = (filename: string) => {
    ref.value.history = ref.value.history.filter(
      (volume) => volume.filename !== filename,
    );
  };

  const updateSettings = (newSettings: Partial<KataKanaConfig>) => {
    ref.value = { ...ref.value, ...newSettings };
  };

  return {
    ref,
    //
    addHistory,
    deleteHistory,
    updateSettings,
  };
};
