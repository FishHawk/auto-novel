<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';
import { VueDraggable } from 'vue-draggable-plus';

import { Locator } from '@/data';
import { SakuraTranslator } from '@/domain/translate';
import { TranslateJob } from '@/model/Translator';
import SoundAllTaskCompleted from '@/sound/all_task_completed.mp3';

import { doAction, useIsWideScreen } from '@/pages/util';
import { WorkspaceJob } from './components/Workspace';

const message = useMessage();
const isWideScreen = useIsWideScreen();

const { setting } = Locator.settingRepository();

const workspace = Locator.sakuraWorkspaceRepository();
const workspaceRef = workspace.ref;

const showCreateWorkerModal = ref(false);

const workspaceJobs = ref<Map<string, WorkspaceJob>>(new Map());

watch(
  () => workspaceRef.value.jobs,
  async (jobs) => {
    for (const job of jobs) {
      if (workspaceJobs.value.has(job.task)) {
        workspaceJobs.value.set(job.task, {
          state: 'pending',
          name: job.description,
          descriptor: job.task,
          createAt: job.createAt,
          tasks: [],
        });
      }
    }
  },
  { immediate: true },
);

const deleteJob = (task: string) => {
  const job = workspaceJobs.value.get(task);
  if (job === undefined) {
    return;
  } else if (job.state === 'processing') {
    message.error('任务被翻译器占用');
  } else if (job.state === 'pending') {
    workspaceJobs.value.delete(task);
    workspace.deleteJob(task);
  } else if (job.state === 'finished') {
    workspaceJobs.value.delete(task);
  }
};
const deleteAllJobs = () => {
  workspaceRef.value.jobs.forEach((job) => {
    if (workspaceJobs.value.get(job.task)?.state === 'processing') {
      return;
    }
    workspace.deleteJob(job.task);
  });
};

const onProgressUpdated = (
  task: string,
  state:
    | { state: 'finish'; abort: boolean }
    | { state: 'processed'; finished: number; error: number; total: number },
) => {
  if (state.state === 'finish') {
    const job = processedJobs.value.get(task)!!;
    processedJobs.value.delete(task);
    if (!state.abort) {
      job.finishAt = Date.now();
      workspace.addJobRecord(job as any);
      workspace.deleteJob(task);
    }
  } else {
    const job = processedJobs.value.get(task)!!;
    job.progress = {
      finished: state.finished,
      error: state.error,
      total: state.total,
    };
  }
};

const clearCache = async () =>
  doAction(
    Locator.cachedSegRepository().then((repo) =>
      repo.clear('sakura-seg-cache'),
    ),
    '缓存清除',
    message,
  );
</script>
