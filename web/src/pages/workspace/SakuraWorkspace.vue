<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { TranslateJob } from '@/model/Translator';
import { notice } from '@/pages/components/NoticeBoard.vue';
import SoundAllTaskCompleted from '@/sound/all_task_completed.mp3';
import { doAction, useIsWideScreen } from '@/pages/util';

const message = useMessage();
const setting = Locator.settingRepository().ref;
const isWideScreen = useIsWideScreen(850);

const workspace = Locator.sakuraWorkspaceRepository();
const workspaceRef = workspace.ref;

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = workspaceRef.value.jobs.find(
    (it) => !processedJobs.value.has(it.task)
  );
  if (job !== undefined) {
    processedJobs.value.set(job.task, job);
  } else if (processedJobs.value.size === 0 && setting.value.workspaceSound) {
    // 全部任务都已经完成
    new Audio(SoundAllTaskCompleted).play();
  }
  return job;
};

const deleteJob = (task: string) => {
  if (processedJobs.value.has(task)) {
    message.error('任务被翻译器占用');
    return;
  }
  workspace.deleteJob(task);
};
const deleteAllJobs = () => {
  workspaceRef.value.jobs.forEach((job) => {
    if (processedJobs.value.has(job.task)) {
      return;
    }
    workspace.deleteJob(job.task);
  });
};

const onProgressUpdated = (
  task: string,
  state:
    | { state: 'finish'; abort: boolean }
    | { state: 'processed'; finished: number; error: number; total: number }
) => {
  if (state.state === 'finish') {
    const job = processedJobs.value.get(task)!!;
    processedJobs.value.delete(task);
    if (!state.abort) {
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
      repo.clear('sakura-seg-cache')
    ),
    '缓存清除',
    message
  );

const notices = [
  notice('目前允许的模型： v0.9b-Q4_K_M 及以上。'),
  notice('AWQ量化版本目前有bug，请不要使用。'),
];
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <n-h1>Sakura工作区</n-h1>

    <notice-board :notices="notices">
      <n-flex>
        <c-a to="/forum/656d60530286f15e3384fcf8" target="_blank">
          本地部署教程
        </c-a>
        /
        <c-a to="/forum/65719bf16843e12bd3a4dc98" target="_blank">
          租用显卡教程
        </c-a>
        /
        <n-a
          href="https://www.autodl.com/console/instance/list"
          target="_blank"
        >
          AutoDL控制台
        </n-a>
        /
        <n-a
          href="https://www.kaggle.com/code/scratchpad/notebookb5b9fe84c5/edit"
          target="_blank"
        >
          Kaggle脚本
        </n-a>
      </n-flex>
    </notice-board>

    <section-header title="翻译器">
      <c-button
        label="添加翻译器"
        :icon="PlusOutlined"
        @action="showCreateWorkerModal = true"
      />

      <n-popconfirm
        :show-icon="false"
        @positive-click="clearCache"
        :negative-text="null"
        style="max-width: 300px"
      >
        <template #trigger>
          <c-button label="清空缓存" :icon="DeleteOutlineOutlined" />
        </template>
        真的要清空缓存吗？
      </n-popconfirm>
    </section-header>

    <n-empty
      v-if="workspaceRef.workers.length === 0"
      description="没有翻译器"
    />
    <n-list>
      <n-list-item v-for="worker of workspaceRef.workers" :key="worker.id">
        <job-worker
          :worker="{ translatorId: 'sakura', ...worker }"
          :get-next-job="getNextJob"
          @update:progress="onProgressUpdated"
        />
      </n-list-item>
    </n-list>

    <section-header title="任务队列">
      <n-popconfirm
        :show-icon="false"
        @positive-click="deleteAllJobs"
        :negative-text="null"
        style="max-width: 300px"
      >
        <template #trigger>
          <c-button label="清空队列" :icon="DeleteOutlineOutlined" />
        </template>
        真的要清空队列吗？
      </n-popconfirm>
    </section-header>
    <n-empty v-if="workspaceRef.jobs.length === 0" description="没有任务" />
    <n-list>
      <n-list-item v-for="job of workspaceRef.jobs" :key="job.task">
        <job-queue
          :job="job"
          :progress="processedJobs.get(job.task)?.progress"
          @top-job="workspace.topJob(job)"
          @bottom-job="workspace.bottomJob(job)"
          @delete-job="deleteJob(job.task)"
        />
      </n-list-item>
    </n-list>

    <job-record-section id="sakura" />

    <template #sidebar>
      <local-volume-list-specific-translation type="sakura" />
    </template>
  </c-layout>

  <sakura-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
