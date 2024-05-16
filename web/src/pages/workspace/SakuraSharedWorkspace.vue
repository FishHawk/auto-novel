<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';
import { VueDraggable } from 'vue-draggable-plus';

import { Locator } from '@/data';
import { TranslateJob } from '@/model/Translator';
import { notice } from '@/pages/components/NoticeBoard.vue';
import SoundAllTaskCompleted from '@/sound/all_task_completed.mp3';
import { doAction, useIsWideScreen } from '@/pages/util';

const message = useMessage();
const isWideScreen = useIsWideScreen(850);

const { setting } = Locator.settingRepository();

const workspace = Locator.sakuraWorkspaceRepository();
const workspaceRef = workspace.ref;

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = workspaceRef.value.jobs.find(
    (it) => !processedJobs.value.has(it.task),
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
    | { state: 'processed'; finished: number; error: number; total: number },
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
      repo.clear('sakura-seg-cache'),
    ),
    '缓存清除',
    message,
  );

const notices = [notice('Akira: 在搞了在搞了，咕咕咕')];
</script>

<template>
  <c-layout :sidebar="false" :sidebar-width="320" class="layout-content">
    <n-h1>Sakura公用队列</n-h1>

    <notice-board :notices="notices"> </notice-board>

    <!-- <section-header title="翻译器">
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
      <vue-draggable
        v-model="workspaceRef.workers"
        :animation="150"
        handle=".drag-trigger"
      >
        <n-list-item v-for="worker of workspaceRef.workers">
          <job-worker
            :worker="{ translatorId: 'sakura', ...worker }"
            :get-next-job="getNextJob"
            @update:progress="onProgressUpdated"
          />
        </n-list-item>
      </vue-draggable>
    </n-list> -->

    <!-- <section-header title="任务队列">
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
      <vue-draggable
        v-model="workspaceRef.jobs"
        :animation="150"
        handle=".drag-trigger"
      >
        <n-list-item v-for="job of workspaceRef.jobs" :key="job.task">
          <job-queue
            :job="job"
            :progress="processedJobs.get(job.task)?.progress"
            @top-job="workspace.topJob(job)"
            @bottom-job="workspace.bottomJob(job)"
            @delete-job="deleteJob(job.task)"
          />
        </n-list-item>
      </vue-draggable>
    </n-list> -->
  </c-layout>
</template>
