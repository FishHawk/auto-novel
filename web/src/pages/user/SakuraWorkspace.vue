<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { ref } from 'vue';

import { useSettingStore } from '@/data/stores/setting';

const message = useMessage();
const setting = useSettingStore();

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
        setting.sakuraWorkers.find(({ id }) => id === value) === undefined,
      message: 'ID不能重复',
      trigger: 'input',
    },
  ],
};

const createSakuraWorker = async (json: {
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
  setting.sakuraWorkers.push(json);
};

const processedTask = ref(new Set<string>());

const getNextJob = () => {
  const job = setting.sakuraJobs.find(
    (it) => !processedTask.value.has(it.task)
  );
  if (job !== undefined) {
    processedTask.value.add(job.task);
  }
  return job;
};

const deleteSakuraJob = async (task: string) => {
  if (task in processedTask.value) {
    message.error('任务被翻译器占用');
    return;
  }
  setting.sakuraJobs = setting.sakuraJobs.filter((j) => j.task !== task);
};

const onJobFinished = (task: string) => {
  processedTask.value.delete(task);
  deleteSakuraJob(task);
};
</script>

<template>
  <user-layout>
    <n-p>
      你可以部署自己的Sakura翻译器，参见
      <n-a href="https://sakura.srpr.moe" target="_blank">
        Sakura模型部署教程
      </n-a>
      。
    </n-p>
    <n-p depth="3">
      启动了的翻译器无法暂停或删除。等这句话没了就可以了。
    </n-p>
    <n-p>
      <n-button @click="showCreateWorkerModal = true">
        添加Sakura翻译器
      </n-button>
    </n-p>

    <n-list>
      <n-list-item v-for="worker of setting.sakuraWorkers" :key="worker.id">
        <sakura-worker
          :id="worker.id"
          :endpoint="worker.endpoint"
          :use-llama-api="worker.useLlamaApi ?? false"
          :get-next-job="getNextJob"
          @finished="onJobFinished"
        />
      </n-list-item>
    </n-list>

    <SectionHeader :title="`任务队列`" />
    <n-table :bordered="false" style="margin-top: 16px">
      <thead>
        <tr>
          <th><b>描述</b></th>
          <th><b>状态</b></th>
          <th><b>信息</b></th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="job of setting.sakuraJobs" :key="job.task">
          <td>
            <n-text depth="3" style="font-size: 12px">{{ job.task }}</n-text>
            <br />
            {{ job.description }}
          </td>
          <td style="white-space: nowrap">
            {{ processedTask.has(job.task) ? '处理中' : '排队中' }}
          </td>
          <td style="white-space: nowrap">
            于<n-time :time="job.createAt" type="relative" />提交
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
