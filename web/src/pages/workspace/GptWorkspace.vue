<script lang="ts" setup>
import {
  DeleteOutlined,
  PlusOutlined,
  RefreshOutlined,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { notice } from '@/components/NoticeBoard.vue';
import {
  TranslateJob,
  migrateGptWorkspace,
  useGptWorkspaceStore,
} from '@/data/stores/workspace';
import { CachedSegRepository } from '@/data/translator';
import { useIsWideScreen } from '@/data/util';

const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();
const isWideScreen = useIsWideScreen(850);

migrateGptWorkspace(gptWorkspace);

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
  await CachedSegRepository.clear('gpt-seg-cache');
  message.success('缓存清除成功');
};

const notices = [
  notice(
    '当您添加翻译器时，若链接留空，则表示将使用OpenAI官方链接，您也可以提供链接以使用第三方GPT代理。'
  ),
  notice(
    '当使用GPT-3.5 Web且选择官方链接时，需要安装相应的插件，详情看使用教程。'
  ),
];
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <n-h1>GPT工作区</n-h1>

    <notice-board :notices="notices">
      <n-flex>
        <RouterNA to="/forum/64f3d63f794cbb1321145c07" target="_blank">
          使用教程
        </RouterNA>
        /
        <n-a href="https://github.com/eujc/ChatGPT" target="_blank">
          帐号注册教程
        </n-a>
        /
        <n-a href="https://chat.openai.com/" target="_blank"> ChatGPT </n-a>
        /
        <n-a href="https://chat.openai.com/api/auth/session" target="_blank">
          AccessToken
        </n-a>
      </n-flex>
    </notice-board>

    <section-header title="翻译器">
      <c-button
        label="添加翻译器"
        :icon="PlusOutlined"
        @action="showCreateWorkerModal = true"
      />
      <c-button label="清空缓存" :icon="DeleteOutlined" @action="clearCache" />
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
        @action="deleteAllJobs()"
      />
    </section-header>
    <n-empty v-if="gptWorkspace.jobs.length === 0" description="没有任务" />
    <n-list>
      <n-list-item v-for="job of gptWorkspace.jobs" :key="job.task">
        <job-queue
          :job="job"
          :progress="processedJobs.get(job.task)?.progress"
          @top-job="gptWorkspace.topJob(job)"
          @bottom-job="gptWorkspace.bottomJob(job)"
          @delete-job="deleteJob(job.task)"
        />
      </n-list-item>
    </n-list>

    <section-header title="任务记录">
      <c-button
        label="重试失败任务"
        :icon="RefreshOutlined"
        @action="gptWorkspace.retryAllJobRecords()"
      />
      <c-button
        label="清空记录"
        :icon="DeleteOutlined"
        @action="gptWorkspace.deleteAllJobRecords()"
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
      <local-volume-list-specific-translation type="gpt" />
    </template>
  </c-layout>

  <gpt-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
