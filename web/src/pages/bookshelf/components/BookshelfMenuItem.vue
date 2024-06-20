<script lang="ts" setup>
import { MoreVertOutlined } from '@vicons/material';
import { FormInst, FormItemRule, FormRules } from 'naive-ui';

import { doAction } from '@/pages/util';
import { useBookshelfStore } from '../BookshelfStore';

const { id, type, title } = defineProps<{
  id: string;
  title: string;
  type: 'web' | 'wenku' | 'local';
}>();

const store = useBookshelfStore();

const message = useMessage();

const options =
  id === 'default'
    ? [{ label: '编辑信息', key: 'edit' }]
    : [
        { label: '编辑信息', key: 'edit' },
        { label: '删除', key: 'delete' },
      ];
const onSelect = (key: string) => {
  if (key === 'edit') {
    showEditModal.value = true;
  } else if (key === 'delete') {
    showDeleteModal.value = true;
  }
};

const showEditModal = ref(false);
const formRef = ref<FormInst | null>(null);
const formValue = ref({ title });
const formRules: FormRules = {
  title: [
    {
      validator: (_rule: FormItemRule, value: string) => value.length > 0,
      message: '收藏夹标题不能为空',
      trigger: 'input',
    },
  ],
};
const updateFavorite = async () => {
  if (formRef.value == null) {
    return;
  } else {
    try {
      await formRef.value.validate();
    } catch (e) {
      return;
    }
  }

  const title = formValue.value.title;

  await doAction(
    store.updateFavored(type, id, title).then(() => {
      showEditModal.value = false;
    }),
    '收藏夹更新',
    message,
  );
};

const showDeleteModal = ref(false);
const deleteFavorite = () =>
  doAction(
    store.deleteFavored(type, id).then(() => {
      showDeleteModal.value = false;
    }),
    '收藏夹删除',
    message,
  );
</script>

<template>
  <router-link :to="`/favorite/${type}/${id}`">
    <n-flex align="center" justify="space-between">
      {{ title }}
      <n-dropdown
        trigger="hover"
        :options="options"
        :keyboard="false"
        @select="onSelect"
      >
        <n-button quaternary circle>
          <n-icon :component="MoreVertOutlined" />
        </n-button>
      </n-dropdown>
    </n-flex>
  </router-link>

  <c-modal v-model:show="showEditModal" title="编辑收藏夹">
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
    </n-form>

    <template #action>
      <c-button
        label="确定"
        require-login
        type="primary"
        @action="updateFavorite"
      />
    </template>
  </c-modal>

  <c-modal v-model:show="showDeleteModal" title="删除收藏夹">
    {{ `确定删除收藏夹<${title}>吗？` }}

    <template #action>
      <c-button
        label="确定"
        require-login
        type="primary"
        @action="deleteFavorite"
      />
    </template>
  </c-modal>
</template>
