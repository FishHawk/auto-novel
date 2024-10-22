<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';
import { VueDraggable } from 'vue-draggable-plus';

import { Locator } from '@/data';
import { TranslateJob } from '@/model/Translator';
import { doAction, useIsWideScreen } from '@/pages/util';

const message = useMessage();
const isWideScreen = useIsWideScreen();

const { setting } = Locator.settingRepository();

const workspace = Locator.gptWorkspaceRepository();
const workspaceRef = workspace.ref;

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = workspace.ref.value.jobs.find(
    (it) => !processedJobs.value.has(it.task),
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
    | { state: 'processed'; finished: number; error: number; total: number },
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

const clearCache = async () =>
  doAction(
    Locator.cachedSegRepository().then((repo) => repo.clear('gpt-seg-cache')),
    '缓存清除',
    message,
  );
</script>

<template>
  <c-layout
    :sidebar="isWideScreen && !setting.hideLocalVolumeListInWorkspace"
    :sidebar-width="320"
    class="layout-content"
  >
    <n-h1>GPT工作区</n-h1>

    <bulletin>
      <n-flex>
        <c-a to="/forum/64f3d63f794cbb1321145c07" target="_blank">
          使用教程
        </c-a>
        /
        <n-a href="https://chat.deepseek.com" target="_blank">
          DeepSeek Chat
        </n-a>
        /
        <n-a href="https://platform.deepseek.com/usage" target="_blank">
          DeepSeek API
        </n-a>
        <n-p> 不再支持GPT web，推荐使用deepseek API，价格很低。 </n-p>
      </n-flex>
    </bulletin>

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
      <vue-draggable
        v-model="workspaceRef.workers"
        :animation="150"
        handle=".drag-trigger"
      >
        <n-list-item v-for="worker of workspaceRef.workers">
          <job-worker
            :worker="{ translatorId: 'gpt', ...worker }"
            :get-next-job="getNextJob"
            @update:progress="onProgressUpdated"
          />
        </n-list-item>
      </vue-draggable>
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
    </n-list>

    <job-record-section id="gpt" />

    <template #sidebar>
      <local-volume-list-specific-translation type="gpt" />
    </template>
  </c-layout>

  <gpt-worker-modal v-model:show="showCreateWorkerModal" />
</template>
