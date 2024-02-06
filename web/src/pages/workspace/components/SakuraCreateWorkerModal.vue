<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules } from 'naive-ui';
import { ref } from 'vue';

import { useSakuraWorkspaceStore } from '@/data/stores/workspace';

const sakuraWorkspace = useSakuraWorkspaceStore();

const formRef = ref<FormInst>();
const formValue = ref({
  id: '',
  endpoint: '',
  useLlamaApi: true,
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
        sakuraWorkspace.workers.find(({ id }) => id === value) === undefined,
      message: '名字不能重复',
      trigger: 'input',
    },
  ],
  endpoint: [
    {
      validator: (rule: FormItemRule, value: string) => value.trim().length > 0,
      message: '链接不能为空',
      trigger: 'input',
    },
  ],
};

const createSakuraWorker = async () => {
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
  sakuraWorkspace.addWorker(worker);
};
</script>

<template>
  <c-modal title="添加Sakura翻译器">
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
      <c-button label="添加" async type="primary" @click="createSakuraWorker" />
    </template>
  </c-modal>
</template>
