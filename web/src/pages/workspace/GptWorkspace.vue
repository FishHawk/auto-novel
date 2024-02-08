<script lang="ts" setup>
import {
  DeleteOutlined,
  RefreshOutlined,
  PlusOutlined,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { TranslateJob, useGptWorkspaceStore } from '@/data/stores/workspace';

import { computePercentage } from './components/util';
import { createSegIndexedDbCache } from '@/data/translator';

const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();

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

const onProgressUpdated = (
  task: string,
  state:
    | { state: 'finish' }
    | { state: 'processed'; finished: number; error: number; total: number }
) => {
  if (state.state === 'finish') {
    const job = processedJobs.value.get(task)!!;
    processedJobs.value.delete(task);
    if (
      job.progress === undefined ||
      job.progress.finished < job.progress.total
    ) {
      gptWorkspace.addUncompletedJob(job as any);
    }
    gptWorkspace.deleteJob(task);
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
</script>

<template>
  <div class="layout-content">
    <n-flex :wrap="false">
      <div>
        <n-h1>GPT工作区</n-h1>

        <n-ul>
          <n-li>
            翻译任务运行在你的浏览器里面，关闭或者刷新本页面都会停止翻译。长时间挂机的话不要把本页面放在后台，防止被浏览器杀掉。
          </n-li>
          <n-li> 启动了的翻译器无法暂停或删除。等这句话没了就可以了。 </n-li>
          <n-li> AccessToken有效期为90天，过期请重新获取。 </n-li>
        </n-ul>

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

        <n-list>
          <n-list-item v-for="worker of gptWorkspace.workers" :key="worker.id">
            <gpt-worker
              :worker="worker"
              :get-next-job="getNextJob"
              @update:progress="onProgressUpdated"
            />
          </n-list-item>
        </n-list>

        <section-header title="任务队列" />
        <n-empty v-if="gptWorkspace.jobs.length === 0" description="没有任务" />
        <n-list>
          <n-list-item v-for="job of gptWorkspace.jobs" :key="job.task">
            <job-queue
              :job="job"
              :percentage="
                computePercentage(processedJobs.get(job.task)?.progress)
              "
              @top-job="gptWorkspace.topJob(job)"
              @delete-job="deleteJob(job.task)"
            />
          </n-list-item>
        </n-list>

        <section-header title="未完成任务记录">
          <c-button
            label="全部重试"
            :icon="RefreshOutlined"
            @click="gptWorkspace.retryAllUncompletedJobs()"
          />
          <c-button
            label="清空记录"
            :icon="DeleteOutlined"
            @click="gptWorkspace.deleteAllUncompletedJobs()"
          />
        </section-header>

        <n-empty
          v-if="gptWorkspace.uncompletedJobs.length === 0"
          description="没有任务"
        />
        <n-list>
          <n-list-item
            v-for="job of gptWorkspace.uncompletedJobs"
            :key="job.task"
          >
            <job-uncompleted
              :job="job"
              @retry-job="gptWorkspace.retryUncompletedJob(job)"
              @delete-job="gptWorkspace.deleteUncompletedJob(job)"
            />
          </n-list-item>
        </n-list>
      </div>

      <n-divider vertical style="height: calc(100vh - 50px); flex: 0 0 1px" />

      <div style="flex: 0 0 350px">
        <local-volume-list
          type="gpt"
          style="
            position: fixed;
            top: 50;
            width: 350px;
            height: calc(100vh - 50px);
          "
        />
      </div>
    </n-flex>
  </div>

  <gpt-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
