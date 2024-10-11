<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';
import { VueDraggable } from 'vue-draggable-plus';

import { Locator } from '@/data';
import { SakuraTranslator } from '@/domain/translate';
import { TranslateJob } from '@/model/Translator';
import SoundAllTaskCompleted from '@/sound/all_task_completed.mp3';

import { doAction, useIsWideScreen } from '@/pages/util';

const message = useMessage();
const isWideScreen = useIsWideScreen();

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

<template>
  <c-layout
    :sidebar="isWideScreen && !setting.hideLocalVolumeListInWorkspace"
    :sidebar-width="320"
    class="layout-content"
  >
    <n-h1>Sakura工作区</n-h1>

    <bulletin>
      <n-flex>
        <c-a to="/forum/656d60530286f15e3384fcf8" target="_blank">
          本地部署教程
        </c-a>
        /
        <span>
          <c-a to="/forum/65719bf16843e12bd3a4dc98" target="_blank">
            AutoDL教程
          </c-a>
          :
          <n-a
            href="https://www.autodl.com/console/instance/list"
            target="_blank"
          >
            控制台
          </n-a>
        </span>
      </n-flex>

      <n-p> 允许上传的模型如下，禁止一切试图突破上传检查的操作。 </n-p>
      <n-ul>
        <n-li v-for="({ repo }, model) in SakuraTranslator.allowModels">
          [
          <n-a
            target="_blank"
            :href="`https://huggingface.co/${repo}/blob/main/${model}.gguf`"
          >
            HF
          </n-a>
          /
          <n-a
            target="_blank"
            :href="`https://hf-mirror.com/${repo}/blob/main/${model}.gguf`"
          >
            国内镜像
          </n-a>
          ]
          {{ model }}
        </n-li>
      </n-ul>
    </bulletin>

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

    <job-record-section id="sakura" />

    <template #sidebar>
      <local-volume-list-specific-translation type="sakura" />
    </template>
  </c-layout>

  <sakura-worker-modal v-model:show="showCreateWorkerModal" />
</template>
