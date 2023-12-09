<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules } from 'naive-ui';
import { ref } from 'vue';

import { GptWorker, useGptWorkspaceStore } from '@/data/stores/workspace';

const gptWorkspace = useGptWorkspaceStore();

const formRef = ref<FormInst>();
const formValue = ref<GptWorker>({
  id: '',
  type: 'web',
  endpoint: '',
  key: '',
});
const formRules: FormRules = {
  id: [
    {
      validator: (rule: FormItemRule, value: string) => value.trim().length > 0,
      message: '名字不能为空',
      trigger: 'input',
    },
    {
      validator: (rule: FormItemRule, value: string) =>
        gptWorkspace.workers.find(({ id }) => id === value) === undefined,
      message: '名字不能重复',
      trigger: 'input',
    },
  ],
  key: [
    {
      validator: (rule: FormItemRule, value: string) => value.trim().length > 0,
      message: 'Key不能为空',
      trigger: 'input',
    },
    {
      validator: (rule: FormItemRule, value: string) =>
        gptWorkspace.workers.find(({ key }) => key === value) === undefined,
      message: 'Key不能重复',
      trigger: 'input',
    },
  ],
};

const createGptWorker = async () => {
  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;

  const worker = { ...formValue.value };
  worker.id = worker.id.trim();
  worker.endpoint = worker.endpoint.trim();
  worker.key = worker.key.trim();
  try {
    const obj = JSON.parse(worker.key);
    worker.key = obj.accessToken;
  } catch {}
  gptWorkspace.addWorker(worker);
};
</script>

<template>
  <card-modal title="添加GPT翻译器">
    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      label-placement="left"
      label-width="auto"
    >
      <n-form-item-row path="id" label="名字">
        <n-input
          v-model:value="formValue.id"
          placeholder="给你的翻译器起个名字"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="type" label="类型">
        <n-radio-group v-model:value="formValue.type" name="type">
          <n-space>
            <n-radio value="web">Web</n-radio>
            <n-radio value="api">Api</n-radio>
          </n-space>
        </n-radio-group>
      </n-form-item-row>
      <n-form-item-row path="endpoint" label="链接">
        <n-input
          v-model:value="formValue.endpoint"
          placeholder="可选，空着表示使用默认值"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
      <n-form-item-row path="key" label="Key">
        <n-input
          v-model:value="formValue.key"
          :placeholder="
            formValue.type === 'web' ? '请输入Access token' : '请输入Api key'
          "
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>
    </n-form>

    <template #action>
      <async-button type="primary" @async-click="() => createGptWorker()">
        添加
      </async-button>
    </template>
  </card-modal>
</template>
