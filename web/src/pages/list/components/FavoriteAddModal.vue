<script lang="ts" setup>
import { FormInst, FormItemRule, FormRules, useMessage } from 'naive-ui';
import { ref } from 'vue';

import { UserRepository } from '@/data/api';
import { doAction } from '@/pages/util';

defineProps(['show']);
const emit = defineEmits<{
  'update:show': [boolean];
  created: [];
}>();

const message = useMessage();

const type = ref<'web' | 'wenku'>('web');

const formRef = ref<FormInst | null>(null);
const formValue = ref({ title: '' });
const formRules: FormRules = {
  title: [
    {
      validator: (rule: FormItemRule, value: string) => value.length > 0,
      message: '收藏夹标题不能为空',
      trigger: 'input',
    },
  ],
};
const addFavorite = async () => {
  if (formRef.value == null) {
    return;
  } else {
    try {
      await formRef.value.validate();
    } catch (e) {
      return;
    }
  }

  const typeValue = type.value;
  const title = formValue.value.title;
  await doAction(
    (typeValue === 'web'
      ? UserRepository.createFavoredWeb({ title })
      : UserRepository.createFavoredWenku({ title })
    ).then(() => {
      emit('created');
      emit('update:show', false);
    }),
    '收藏夹创建',
    message
  );
};
</script>

<template>
  <c-modal
    title="新建收藏夹"
    :show="show"
    @update:show="emit('update:show', $event)"
  >
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
        <n-radio-group v-model:value="type" name="type">
          <n-flex>
            <n-radio value="web"> 网页小说 </n-radio>
            <n-radio value="wenku"> 文库小说 </n-radio>
          </n-flex>
        </n-radio-group>
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
