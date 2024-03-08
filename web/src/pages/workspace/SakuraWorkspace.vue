<script lang="ts" setup>
import {
  DeleteOutlined,
  PlusOutlined,
  RefreshOutlined,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { notice } from '@/components/NoticeBoard.vue';
import { useSettingStore } from '@/data/stores/setting';
import { TranslateJob, useSakuraWorkspaceStore } from '@/data/stores/workspace';
import { createSegIndexedDbCache } from '@/data/translator';
import { useIsWideScreen } from '@/data/util';
import SoundAllTaskCompleted from '@/sound/all_task_completed.mp3';

const message = useMessage();
const setting = useSettingStore();
const sakuraWorkspace = useSakuraWorkspaceStore();
const isWideScreen = useIsWideScreen(850);

const showCreateWorkerModal = ref(false);

type ProcessedJob = TranslateJob & {
  progress?: { finished: number; error: number; total: number };
};

const processedJobs = ref<Map<string, ProcessedJob>>(new Map());

const getNextJob = () => {
  const job = sakuraWorkspace.jobs.find(
    (it) => !processedJobs.value.has(it.task)
  );
  if (job !== undefined) {
    processedJobs.value.set(job.task, job);
  } else if (processedJobs.value.size === 0 && setting.workspaceSound) {
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
  sakuraWorkspace.deleteJob(task);
};
const deleteAllJobs = () => {
  sakuraWorkspace.jobs.forEach((job) => {
    if (processedJobs.value.has(job.task)) {
      return;
    }
    sakuraWorkspace.deleteJob(job.task);
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
      sakuraWorkspace.addJobRecord(job as any);
      sakuraWorkspace.deleteJob(task);
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
  const cache = await createSegIndexedDbCache('sakura-seg-cache');
  await cache.clear();
  message.success('缓存清除成功');
};

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
        <RouterNA to="/forum/656d60530286f15e3384fcf8" target="_blank">
          本地部署教程
        </RouterNA>
        /
        <RouterNA to="/forum/65719bf16843e12bd3a4dc98" target="_blank">
          租用显卡教程
        </RouterNA>
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
      v-if="sakuraWorkspace.workers.length === 0"
      description="没有翻译器"
    />
    <n-list>
      <n-list-item v-for="worker of sakuraWorkspace.workers" :key="worker.id">
        <job-worker
          :worker="{ translatorId: 'sakura', ...worker }"
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
    <n-empty v-if="sakuraWorkspace.jobs.length === 0" description="没有任务" />
    <n-list>
      <n-list-item v-for="job of sakuraWorkspace.jobs" :key="job.task">
        <job-queue
          :job="job"
          :progress="processedJobs.get(job.task)?.progress"
          @top-job="sakuraWorkspace.topJob(job)"
          @bottom-job="sakuraWorkspace.bottomJob(job)"
          @delete-job="deleteJob(job.task)"
        />
      </n-list-item>
    </n-list>

    <section-header title="任务记录">
      <c-button
        label="重试失败任务"
        :icon="RefreshOutlined"
        @action="sakuraWorkspace.retryAllJobRecords()"
      />
      <c-button
        label="清空记录"
        :icon="DeleteOutlined"
        @action="sakuraWorkspace.deleteAllJobRecords()"
      />
    </section-header>

    <n-empty
      v-if="sakuraWorkspace.uncompletedJobs.length === 0"
      description="没有任务"
    />
    <n-list>
      <n-list-item
        v-for="job of sakuraWorkspace.uncompletedJobs"
        :key="job.task"
      >
        <job-record
          :job="job"
          @retry-job="sakuraWorkspace.retryJobRecord(job)"
          @delete-job="sakuraWorkspace.deleteJobRecord(job)"
        />
      </n-list-item>
    </n-list>

    <template #sidebar>
      <local-volume-list-specific-translation type="sakura" />
    </template>
  </c-layout>

  <sakura-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
