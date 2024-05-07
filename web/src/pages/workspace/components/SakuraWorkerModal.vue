<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { Locator } from '@/data';
import { SakuraWorker } from '@/model/Translator';

const props = defineProps<{
  show: boolean;
  worker?: SakuraWorker;
}>();
const emit = defineEmits<{
  (e: 'update:show', show: boolean): void;
}>();

const workspace = Locator.sakuraWorkspaceRepository();
const workspaceRef = workspace.ref;

const initFormValue = () => {
  const worker = props.worker;
  if (worker === undefined) {
    return {
      id: '',
      endpoint: '',
    };
  } else {
    return { ...worker };
  }
};

const formRef = ref<FormInst>();
const formValue = ref(initFormValue());
const formRules: FormRules = {
  id: [
    {
      validator: (rule: FormItemRule, value: string) => value.trim().length > 0,
      message: '名字不能为空',
      trigger: 'input',
    },
    {
      validator: (rule: FormItemRule, value: string) =>
        workspaceRef.value.workers
          .filter(({ id }) => id !== props.worker?.id)
          .find(({ id }) => id === value) === undefined,
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

const submit = async () => {
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

  if (props.worker === undefined) {
    workspace.addWorker(worker);
  } else {
    const index = workspaceRef.value.workers.findIndex(
      ({ id }) => id === props.worker?.id
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
    :title="verb + 'Sakura翻译器'"
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
      <n-form-item-row path="endpoint" label="链接">
        <n-input
          v-model:value="formValue.endpoint"
          placeholder="翻译器的链接"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-text depth="3" style="font-size: 12px">
        # 链接例子：http://127.0.0.1:8080
      </n-text>
    </n-form>

    <template #action>
      <c-button :label="verb" type="primary" @action="submit" />
    </template>
  </c-modal>
</template>
