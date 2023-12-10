<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import {
  TranslateJob,
  UncompletedTranslateJob,
  useGptWorkspaceStore,
} from '@/data/stores/workspace';

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

const deleteGptJob = async (task: string) => {
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
      job.progress !== undefined &&
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

const computePercentage = (job: ProcessedJob) => {
  if (job.progress) {
    const { finished, error, total } = job.progress;
    if (total === 0) {
      return 100;
    } else {
      return Math.round((1000 * (finished + error)) / total) / 10;
    }
  } else {
    return 0;
  }
};

const retryGptJob = (job: UncompletedTranslateJob): void => {
  const success = gptWorkspace.addJob({
    task: job.task,
    description: job.description,
    createAt: Date.now(),
  });
  if (success) {
    gptWorkspace.deleteUncompletedJob(job.task);
    message.success('排队成功');
  } else {
    message.error('GPT翻译任务已经存在');
  }
};

const clearCache = async () => {
  await (
    await import('@/data/translator/cache')
  ).clearSegIndexedDbCache('gpt-seg-cache');
  message.success('缓存清除成功');
};
</script>

<template>
  <user-layout>
    <n-p> 使用说明： </n-p>
    <n-ul>
      <n-li>
        翻译任务运行在你的浏览器里面，关闭或者刷新本页面都会停止翻译。长时间挂机的话不要把本页面放在后台，防止被浏览器杀掉。
      </n-li>
      <n-li>
        启动前先在网络小说/文库小说/文件翻译页面点击排队添加任务，完成后回到之前的页面查看翻译结果。
      </n-li>
      <n-li> 启动了的翻译器无法暂停或删除。等这句话没了就可以了。 </n-li>
      <n-li> AccessToken有效期为90天，过期请重新获取。 </n-li>
    </n-ul>

    <n-p>
      <n-space>
        <n-button @click="showCreateWorkerModal = true">
          添加GPT翻译器
        </n-button>

        <async-button @async-click="clearCache"> 删除GPT缓存 </async-button>
      </n-space>
    </n-p>

    <n-list>
      <n-list-item v-for="worker of gptWorkspace.workers" :key="worker.id">
        <gpt-worker
          :worker="worker"
          :get-next-job="getNextJob"
          @update:progress="onProgressUpdated"
        />
      </n-list-item>
    </n-list>

    <SectionHeader title="任务队列" />
    <n-empty v-if="gptWorkspace.jobs.length === 0" description="没有任务" />
    <n-table :bordered="false" style="margin-top: 16px" v-else>
      <thead>
        <tr>
          <th><b>描述</b></th>
          <th><b>信息</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job of gptWorkspace.jobs" :key="job.task">
          <td>
            <n-text depth="3" style="font-size: 12px">{{ job.task }}</n-text>
            <br />
            {{ job.description }}
            <template v-if="processedJobs.has(job.task)">
              <br />
              <n-progress
                :percentage="computePercentage(processedJobs.get(job.task)!!)"
                style="max-width: 600px"
              />
            </template>
          </td>
          <td style="white-space: nowrap">
            <n-time :time="job.createAt" type="relative" />
            <br />
            <async-button
              type="error"
              text
              @async-click="() => deleteGptJob(job.task)"
            >
              删除
            </async-button>
          </td>
        </tr>
      </tbody>
    </n-table>

    <SectionHeader title="未完成任务记录">
      <n-button @click="gptWorkspace.clearUncompletedJobs()"> 清空 </n-button>
    </SectionHeader>
    <n-empty
      v-if="gptWorkspace.uncompletedJobs.length === 0"
      description="没有任务"
    />
    <n-table :bordered="false" style="margin-top: 16px" v-else>
      <thead>
        <tr>
          <th><b>描述</b></th>
          <th><b>信息</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job of gptWorkspace.uncompletedJobs" :key="job.task">
          <td>
            <n-text depth="3" style="font-size: 12px">{{ job.task }}</n-text>
            <br />
            {{ job.description }}
            <template v-if="job.progress">
              <br />
              总共 {{ job.progress?.total }} / 成功
              {{ job.progress?.finished }} / 失败 {{ job.progress?.error }}
            </template>
          </td>
          <td style="white-space: nowrap">
            <n-time :time="job.createAt" type="relative" />
            <br />
            <n-button type="primary" text @click="() => retryGptJob(job)">
              重新加入
            </n-button>
            <br />
            <n-button
              type="error"
              text
              @click="() => gptWorkspace.deleteUncompletedJob(job.task)"
            >
              删除
            </n-button>
          </td>
        </tr>
      </tbody>
    </n-table>
  </user-layout>

  <gpt-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
