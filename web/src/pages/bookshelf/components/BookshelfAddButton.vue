<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { doAction } from '@/pages/util';
import { useBookshelfStore } from '../BookshelfStore';

const message = useMessage();

const store = useBookshelfStore();

const showAddModal = ref(false);

const formRef = ref<FormInst>();
const formValue = ref<{
  title: string;
  type: 'web' | 'wenku' | 'local';
}>({
  title: '',
  type: 'web',
});
const formRules: FormRules = {
  title: [
    {
      validator: (_rule: FormItemRule, value: string) => value.length > 0,
      message: '收藏夹标题不能为空',
      trigger: 'input',
    },
    {
      validator: (_rule: FormItemRule, value: string) => value.length <= 20,
      message: '收藏夹标题至多为20个字符',
      trigger: 'input',
    },
  ],
};

const addFavorite = async () => {
  try {
    await formRef.value?.validate();
  } catch (e) {
    return;
  }

  const { type, title } = formValue.value;
  await doAction(
    store.createFavored(type, title).then(() => {
      showAddModal.value = false;
    }),
    '收藏夹创建',
    message,
  );
};
</script>

<template>
  <c-button label="新建" :icon="PlusOutlined" @action="showAddModal = true" />

  <c-modal title="新建收藏夹" v-model:show="showAddModal">
    <n-form
      ref="formRef"
      :model="formValue"
      :rules="formRules"
      label-placement="left"
      label-width="auto"
    >
      <n-form-item-row label="标题" path="title">
        <n-input
          v-model:value="formValue.title"
          placeholder="收藏夹标题"
          :input-props="{ spellcheck: false }"
        />
      </n-form-item-row>

      <n-form-item-row label="类型">
        <c-radio-group
          v-model:value="formValue.type"
          :options="[
            { label: '网页小说', value: 'web' },
            { label: '文库小说', value: 'wenku' },
            { label: '本地小说', value: 'local' },
          ]"
        />
      </n-form-item-row>
    </n-form>

    <template #action>
      <c-button
        label="确定"
        require-login
        type="primary"
        @action="addFavorite"
      />
    </template>
  </c-modal>
</template>
