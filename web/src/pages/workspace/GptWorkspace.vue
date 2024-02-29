<script lang="ts" setup>
import {
  DeleteOutlined,
  PlusOutlined,
  RefreshOutlined,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { notice } from '@/components/NoticeBoard.vue';
import { TranslateJob, useGptWorkspaceStore } from '@/data/stores/workspace';
import { createSegIndexedDbCache } from '@/data/translator';
import { useIsWideScreen } from '@/data/util';

import { computePercentage } from './components/util';

const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();
const isWideScreen = useIsWideScreen(850);

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = gptWorkspace.jobs.find((it) => !processedJobs.value.has(it.task));
  if (job !== undefined) {
    processedJobs.value.set(job.task, job);
  }
  return job;
};

const deleteJob = (task: string) => {
  if (processedJobs.value.has(task)) {
    message.error('任务被翻译器占用');
    return;
  }
  gptWorkspace.deleteJob(task);
};
const deleteAllJobs = () => {
  gptWorkspace.jobs.forEach((job) => {
    if (processedJobs.value.has(job.task)) {
      return;
    }
    gptWorkspace.deleteJob(job.task);
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
      gptWorkspace.addJobRecord(job);
      gptWorkspace.deleteJob(task);
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

const clearCache = async () => {
  const cache = await createSegIndexedDbCache('gpt-seg-cache');
  await cache.clear();
  message.success('缓存清除成功');
};

const notices = [
  notice(
    'GPT3.5 web 不再使用中转，而是使用官网链接，需要你的网络环境能正常访问ChatGPT并安装插件。你仍然可以在添加翻译器的时候设置中转链接。',
    true
  ),
];
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <n-h1>GPT工作区</n-h1>

    <notice-board :notices="notices" />

    <section-header title="翻译器">
      <c-button
        label="添加翻译器"
        :icon="PlusOutlined"
        @click="showCreateWorkerModal = true"
      />
      <c-button
        label="清空缓存"
        :icon="DeleteOutlined"
        async
        @click="clearCache"
      />
    </section-header>

    <n-empty
      v-if="gptWorkspace.workers.length === 0"
      description="没有翻译器"
    />
    <n-list>
      <n-list-item v-for="worker of gptWorkspace.workers" :key="worker.id">
        <job-worker
          :worker="{ translatorId: 'gpt', ...worker }"
          :get-next-job="getNextJob"
          @update:progress="onProgressUpdated"
        />
      </n-list-item>
    </n-list>

    <section-header title="任务队列">
      <c-button
        label="清空队列"
        :icon="DeleteOutlined"
        @click="deleteAllJobs()"
      />
    </section-header>
    <n-empty v-if="gptWorkspace.jobs.length === 0" description="没有任务" />
    <n-list>
      <n-list-item v-for="job of gptWorkspace.jobs" :key="job.task">
        <job-queue
          :job="job"
          :percentage="computePercentage(processedJobs.get(job.task)?.progress)"
          @top-job="gptWorkspace.topJob(job)"
          @delete-job="deleteJob(job.task)"
        />
      </n-list-item>
    </n-list>

    <section-header title="任务记录">
      <c-button
        label="重试"
        :icon="RefreshOutlined"
        @click="gptWorkspace.retryAllJobRecords()"
      />
      <c-button
        label="清空记录"
        :icon="DeleteOutlined"
        @click="gptWorkspace.deleteAllJobRecords()"
      />
    </section-header>

    <n-empty
      v-if="gptWorkspace.uncompletedJobs.length === 0"
      description="没有任务"
    />
    <n-list>
      <n-list-item v-for="job of gptWorkspace.uncompletedJobs" :key="job.task">
        <job-record
          :job="job"
          @retry-job="gptWorkspace.retryJobRecord(job)"
          @delete-job="gptWorkspace.deleteJobRecord(job)"
        />
      </n-list-item>
    </n-list>

    <template #sidebar>
      <local-volume-list type="gpt" />
    </template>
  </c-layout>

  <gpt-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
