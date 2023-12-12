<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import {
  TranslateJob,
  UncompletedTranslateJob,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';

const message = useMessage();
const sakuraWorkspace = useSakuraWorkspaceStore();

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
  }
  return job;
};

const deleteSakuraJob = async (task: string) => {
  if (processedJobs.value.has(task)) {
    message.error('任务被翻译器占用');
    return;
  }
  sakuraWorkspace.deleteJob(task);
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
      sakuraWorkspace.addUncompletedJob(job as any);
    }
    sakuraWorkspace.deleteJob(task);
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

const retrySakuraJob = (job: UncompletedTranslateJob): void => {
  const success = sakuraWorkspace.addJob({
    task: job.task,
    description: job.description,
    createAt: Date.now(),
  });
  if (success) {
    sakuraWorkspace.deleteUncompletedJob(job.task);
    message.success('排队成功');
  } else {
    message.error('Sakura翻译任务已经存在');
  }
};

const clearCache = async () => {
  await (
    await import('@/data/translator/cache')
  ).clearSegIndexedDbCache('sakura-seg-cache');
  message.success('缓存清除成功');
};
</script>

<template>
  <main-layout>
    <n-h1>Sakura工作区</n-h1>

    <n-p>使用说明：</n-p>
    <n-ul>
      <n-li>
        翻译任务运行在你的浏览器里面，关闭或者刷新本页面都会停止翻译。长时间挂机的话不要把本页面放在后台，防止被浏览器杀掉。
      </n-li>
      <n-li>
        启动前先在文件翻译页面点击排队添加任务，完成后回到之前的页面查看翻译结果。
      </n-li>
      <n-li> 启动了的翻译器无法暂停或删除。等这句话没了就可以了。 </n-li>
      <n-li>
        Sakura部署教程参见
        <RouterNA to="/forum/656d60530286f15e3384fcf8"> 本地部署教程 </RouterNA>
        和
        <RouterNA to="/forum/65719bf16843e12bd3a4dc98"> 租用显卡教程 </RouterNA>
      </n-li>
    </n-ul>

    <n-p>
      <n-space>
        <n-button @click="showCreateWorkerModal = true">
          添加Sakura翻译器
        </n-button>

        <async-button @async-click="clearCache"> 删除Sakura缓存 </async-button>
      </n-space>
    </n-p>

    <n-list>
      <n-list-item v-for="worker of sakuraWorkspace.workers" :key="worker.id">
        <sakura-worker
          :id="worker.id"
          :endpoint="worker.endpoint"
          :use-llama-api="worker.useLlamaApi ?? false"
          :get-next-job="getNextJob"
          @update:progress="onProgressUpdated"
        />
      </n-list-item>
    </n-list>

    <SectionHeader title="任务队列" />
    <n-empty v-if="sakuraWorkspace.jobs.length === 0" description="没有任务" />
    <n-table :bordered="false" style="margin-top: 16px" v-else>
      <thead>
        <tr>
          <th><b>描述</b></th>
          <th><b>信息</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job of sakuraWorkspace.jobs" :key="job.task">
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
              @async-click="() => deleteSakuraJob(job.task)"
            >
              删除
            </async-button>
          </td>
        </tr>
      </tbody>
    </n-table>

    <SectionHeader title="未完成任务记录">
      <n-button @click="sakuraWorkspace.clearUncompletedJobs()">
        清空
      </n-button>
    </SectionHeader>
    <n-empty
      v-if="sakuraWorkspace.uncompletedJobs.length === 0"
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
        <tr v-for="job of sakuraWorkspace.uncompletedJobs" :key="job.task">
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
            <n-button type="primary" text @click="() => retrySakuraJob(job)">
              重新加入
            </n-button>
            <br />
            <n-button
              type="error"
              text
              @click="() => sakuraWorkspace.deleteUncompletedJob(job.task)"
            >
              删除
            </n-button>
          </td>
        </tr>
      </tbody>
    </n-table>
  </main-layout>

  <sakura-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
