<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { TranslateJob } from '@/model/Translator';
import { notice } from '@/pages/components/NoticeBoard.vue';
import { useIsWideScreen } from '@/pages/util';

const message = useMessage();
const isWideScreen = useIsWideScreen(850);

const workspace = Locator.gptWorkspaceRepository();
const workspaceRef = workspace.ref;

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = workspace.ref.value.jobs.find(
    (it) => !processedJobs.value.has(it.task)
  );
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
      workspace.addJobRecord(job);
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

const clearCache = async () => {
  const repo = await Locator.cachedSegRepository();
  await repo.clear('gpt-seg-cache');
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
        <c-a to="/forum/64f3d63f794cbb1321145c07" target="_blank">
          使用教程
        </c-a>
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
      <c-button
        label="清空缓存"
        :icon="DeleteOutlineOutlined"
        @action="clearCache"
      />
    </section-header>

    <n-empty
      v-if="workspaceRef.workers.length === 0"
      description="没有翻译器"
    />
    <n-list>
      <n-list-item v-for="worker of workspaceRef.workers" :key="worker.id">
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
        :icon="DeleteOutlineOutlined"
        @action="deleteAllJobs()"
      />
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

    <job-record-section id="gpt" />

    <template #sidebar>
      <local-volume-list-specific-translation type="gpt" />
    </template>
  </c-layout>

  <gpt-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
