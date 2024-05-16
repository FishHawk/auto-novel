<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { Locator } from '@/data';
import { GptWorker } from '@/model/Translator';

const props = defineProps<{
  show: boolean;
  worker?: GptWorker;
}>();
const emit = defineEmits<{
  (e: 'update:show', show: boolean): void;
}>();

const workspace = Locator.gptWorkspaceRepository();
const workspaceRef = workspace.ref;

const initFormValue = (): {
  id: string;
  type: 'web' | 'api';
  modelWeb: string;
  modelApi: string;
  endpointWeb: string;
  endpointApi: string;
  key: string;
} => {
  const worker = props.worker;
  if (worker === undefined) {
    return {
      id: '',
      type: 'web',
      modelWeb: 'text-davinci-002-render-sha',
      modelApi: 'gpt-3.5-turbo',
      endpointWeb: 'https://chat.openai.com/backend-api',
      endpointApi: 'https://api.openai.com',
      key: '',
    };
  } else {
    return {
      id: worker.id,
      type: worker.type,
      modelWeb:
        worker.type === 'web' ? worker.model : 'text-davinci-002-render-sha',
      modelApi: worker.type === 'api' ? worker.model : 'gpt-3.5-turbo',
      endpointWeb:
        worker.type === 'web'
          ? worker.endpoint
          : 'https://chat.openai.com/backend-api',
      endpointApi:
        worker.type === 'api' ? worker.endpoint : 'https://api.openai.com',
      key: worker.key,
    };
  }
};

const formRef = ref<FormInst>();
const formValue = ref(initFormValue());

const emptyCheck = (name: string) => ({
  validator: (rule: FormItemRule, value: string) => value.trim().length > 0,
  message: name + '不能为空',
  trigger: 'input',
});

const formRules: FormRules = {
  id: [
    emptyCheck('名字'),
    {
      validator: (rule: FormItemRule, value: string) =>
        workspaceRef.value.workers
          .filter(({ id }) => id !== props.worker?.id)
          .find(({ id }) => id === value) === undefined,
      message: '名字不能重复',
      trigger: 'input',
    },
  ],
  modelWeb: [emptyCheck('模型')],
  modelApi: [emptyCheck('模型')],
  endpointWeb: [emptyCheck('链接')],
  endpointApi: [emptyCheck('链接')],
  key: [
    emptyCheck('Key'),
    {
      validator: (rule: FormItemRule, value: string) =>
        workspaceRef.value.workers
          .filter(({ id }) => id !== props.worker?.id)
          .find(({ key }) => key === value) === undefined,
      message: 'Key不能重复',
      trigger: 'input',
    },
  ],
};

const submit = async () => {
  const validated = await new Promise<boolean>(function (resolve, _reject) {
    formRef.value?.validate((errors) => {
      if (errors) resolve(false);
      else resolve(true);
    });
  });
  if (!validated) return;

  const { id, type, modelWeb, modelApi, endpointWeb, endpointApi, key } =
    formValue.value;
  const worker = {
    id: id.trim(),
    type,
    model: type === 'web' ? modelWeb.trim() : modelApi.trim(),
    endpoint: type === 'web' ? endpointWeb.trim() : endpointApi.trim(),
    key: key.trim(),
  };
  try {
    const obj = JSON.parse(worker.key);
    if (typeof obj.accessToken === 'string') {
      worker.key = obj.accessToken;
    }
  } catch {}

  if (props.worker === undefined) {
    workspace.addWorker(worker);
  } else {
    const index = workspaceRef.value.workers.findIndex(
      ({ id }) => id === props.worker?.id,
    );
    workspaceRef.value.workers[index] = worker;
    emit('update:show', false);
  }
};

const verb = computed(() => (props.worker === undefined ? '添加' : '更新'));
</script>

<template>
  <c-modal
    :show="show"
    @update:show="$emit('update:show', $event)"
    :title="verb + 'GPT翻译器'"
  >
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
          <n-flex>
            <n-radio value="web">Web</n-radio>
            <n-radio value="api">Api</n-radio>
          </n-flex>
        </n-radio-group>
      </n-form-item-row>

      <template v-if="formValue.type === 'web'">
        <n-form-item-row path="modelWeb" label="模型">
          <n-input
            v-model:value="formValue.modelWeb"
            disabled
            placeholder="模型名称"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
        <n-form-item-row path="endpointWeb" label="链接">
          <n-input
            v-model:value="formValue.endpointWeb"
            placeholder="OpenAI链接，可以使用第三方中转"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
      </template>

      <template v-else>
        <n-form-item-row path="modelApi" label="模型">
          <n-input
            v-model:value="formValue.modelApi"
            placeholder="模型名称"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
        <n-form-item-row path="endpointApi" label="链接">
          <n-input
            v-model:value="formValue.endpointApi"
            placeholder="OpenAI链接，可以使用第三方中转"
            :input-props="{ spellcheck: false }"
          />
        </n-form-item-row>
      </template>

      <n-form-item-row path="key" label="Key">
        <n-input
          v-model:value="formValue.key"
          :placeholder="
            formValue.type === 'web' ? '请输入Access token' : '请输入Api key'
          "
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-text depth="3" style="font-size: 12px">
        {{
          formValue.type === 'web'
            ? '# 链接例子：https://chatgpt-proxy.lss233.com/api'
            : '# 链接例子：https://gpt.mnxcc.com，后面不要加‘/v1’'
        }}
      </n-text>
    </n-form>

    <template #action>
      <c-button label="添加" type="primary" @action="submit" />
    </template>
  </c-modal>
</template>
