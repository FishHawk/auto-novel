<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { TranslateJob, useSakuraWorkspaceStore } from '@/data/stores/workspace';

import { computePercentage } from './components/util';

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

const deleteJob = (task: string) => {
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
      job.progress === undefined ||
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

const clearCache = async () => {
  await import('@/data/translator')
    .then((it) => it.createSegIndexedDbCache('sakura-seg-cache'))
    .then((it) => it.clear());
  message.success('缓存清除成功');
};
</script>

<template>
  <div class="layout-content">
    <n-flex :wrap="false">
      <div>
        <n-h1>Sakura工作区</n-h1>
        <n-ul>
          <n-li>
            翻译任务运行在你的浏览器里面，关闭或者刷新本页面都会停止翻译。长时间挂机的话不要把本页面放在后台，防止被浏览器杀掉。
          </n-li>
          <n-li> 启动了的翻译器无法暂停或删除。等这句话没了就可以了。 </n-li>
          <n-li>
            Sakura部署教程参见
            <RouterNA to="/forum/656d60530286f15e3384fcf8">
              本地部署教程
            </RouterNA>
            和
            <RouterNA to="/forum/65719bf16843e12bd3a4dc98">
              租用显卡教程
            </RouterNA>
            。
          </n-li>
          <n-li>
            如果你想直接翻译网络小说/文库小说，请确保你的模型版本允许上传。当前推荐的版本：
            <n-a
              href="https://huggingface.co/SakuraLLM/Sakura-13B-LNovel-v0.9b-GGUF/blob/main/sakura-13b-lnovel-v0.9b-Q4_K_M.gguf"
              target="_blank"
            >
              v0.9b-Q4_K_M
            </n-a>
            。AWQ量化版本目前有bug，请不要使用。
          </n-li>
        </n-ul>

        <n-p>
          <n-space>
            <n-button @click="showCreateWorkerModal = true">
              添加Sakura翻译器
            </n-button>

            <async-button @async-click="clearCache">
              删除Sakura缓存
            </async-button>
          </n-space>
        </n-p>

        <n-list>
          <n-list-item
            v-for="worker of sakuraWorkspace.workers"
            :key="worker.id"
          >
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
        <n-empty
          v-if="sakuraWorkspace.jobs.length === 0"
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
            <tr v-for="job of sakuraWorkspace.jobs" :key="job.task">
              <td>
                <n-text depth="3" style="font-size: 12px">{{
                  job.task
                }}</n-text>
                <br />
                {{ job.description }}
                <template v-if="processedJobs.has(job.task)">
                  <br />
                  <n-progress
                    :percentage="
                      computePercentage(processedJobs.get(job.task)?.progress)
                    "
                    style="max-width: 600px"
                  />
                </template>
              </td>
              <td style="white-space: nowrap">
                <n-time :time="job.createAt" type="relative" />
                <br />
                <n-button
                  type="primary"
                  text
                  @click="() => sakuraWorkspace.topJob(job)"
                >
                  置顶
                </n-button>
                <n-button
                  type="error"
                  text
                  @click="deleteJob(job.task)"
                  style="margin-left: 8px"
                >
                  删除
                </n-button>
              </td>
            </tr>
          </tbody>
        </n-table>

        <SectionHeader title="未完成任务记录">
          <n-space :wrap="false">
            <n-button @click="sakuraWorkspace.retryAllUncompletedJobs()">
              全部重试
            </n-button>
            <n-button @click="sakuraWorkspace.deleteAllUncompletedJobs()">
              清空
            </n-button>
          </n-space>
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
                <n-text depth="3" style="font-size: 12px">{{
                  job.task
                }}</n-text>
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
                <n-button
                  type="primary"
                  text
                  @click="() => sakuraWorkspace.retryUncompletedJob(job)"
                >
                  重新加入
                </n-button>
                <br />
                <n-button
                  type="error"
                  text
                  @click="() => sakuraWorkspace.deleteUncompletedJob(job)"
                >
                  删除
                </n-button>
              </td>
            </tr>
          </tbody>
        </n-table>
      </div>

      <n-divider vertical style="height: calc(100vh - 50px); flex: 0 0 1px" />

      <div style="flex: 0 0 350px">
        <div :wrap="false" style="position: fixed; top: 50; width: 350px">
          <local-volume-list type="sakura" />
        </div>
      </div>
    </n-flex>
  </div>

  <sakura-create-worker-modal v-model:show="showCreateWorkerModal" />
</template>
