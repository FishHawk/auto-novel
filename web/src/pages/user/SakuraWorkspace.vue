<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { ref } from 'vue';

import { useSettingStore } from '@/data/stores/setting';
import {
  TranslateJob,
  UncompletedTranslateJob,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';

const message = useMessage();
const sakuraWorkspace = useSakuraWorkspaceStore();

const showCreateWorkerModal = ref(false);
const formRef = ref<FormInst>();
const formValue = ref({
  id: '',
  endpoint: '',
  useLlamaApi: true,
});
const formRules: FormRules = {
  id: [
    {
      validator: (rule: FormItemRule, value: string) =>
        sakuraWorkspace.workers.find(({ id }) => id === value) === undefined,
      message: 'ID不能重复',
      trigger: 'input',
    },
  ],
};

const createSakuraWorker = async (worker: {
  id: string;
  endpoint: string;
  useLlamaApi: boolean;
}) => {
  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;
  sakuraWorkspace.addWorker(worker);
};

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
</script>

<template>
  <user-layout>
    <n-p depth="3"> 启动了的翻译器无法暂停或删除。等这句话没了就可以了。 </n-p>

    <n-p>
      <n-space>
        <n-button @click="showCreateWorkerModal = true">
          添加Sakura翻译器
        </n-button>

        <RouterNA to="/forum/656d60530286f15e3384fcf8">
          <n-button>本地部署教程</n-button>
        </RouterNA>

        <RouterNA to="/forum/65719bf16843e12bd3a4dc98">
          <n-button>租用显卡教程</n-button>
        </RouterNA>
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
  </user-layout>

  <card-modal v-model:show="showCreateWorkerModal" title="添加Sakura翻译器">
    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      label-placement="left"
    >
      <n-form-item-row path="id" label="名字">
        <n-input
          v-model:value="formValue.id"
          placeholder="给你的翻译器起个名字"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="endpoint" label="链接">
        <n-input
          v-model:value="formValue.endpoint"
          placeholder="翻译器的链接"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="useLlamaApi" label="LlamaApi">
        <n-switch v-model:value="formValue.useLlamaApi" />
      </n-form-item-row>

      <n-text depth="3" style="font-size: 12px">
        # 链接例子：http://127.0.0.1:8080
      </n-text>
    </n-form>

    <template #action>
      <async-button
        type="primary"
        @async-click="() => createSakuraWorker(formValue)"
      >
        添加
      </async-button>
    </template>
  </card-modal>
</template>
