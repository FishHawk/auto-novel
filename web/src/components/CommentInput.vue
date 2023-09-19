<script lang="ts" setup>
import { ref } from 'vue';

import { ApiComment } from '@/data/api/api_comment';
import { useUserDataStore } from '@/data/stores/userData';
import { useMessage } from 'naive-ui';

const { site, parent } = withDefaults(
  defineProps<{
    site: string;
    parent?: string;
    placeholder?: string;
  }>(),
  { parent: undefined }
);

const emit = defineEmits<{ replied: [] }>();

const message = useMessage();
const userData = useUserDataStore();

const content = ref('');

async function reply() {
  if (!userData.logined) {
    message.info('请先登录');
    return;
  }

  if (content.value.length === 0) {
    message.info('回复内容不能为空');
  } else {
    const result = await ApiComment.reply(site, parent, content.value);
    if (result.ok) {
      content.value = '';
      message.info('回复发布成功');
      emit('replied');
    } else {
      message.error('回复发布错误：' + result.error.message);
    }
  }
}
</script>

<template>
  <n-input
    v-model:value="content"
    type="textarea"
    :placeholder="placeholder"
    :autosize="{
      minRows: 2,
      maxRows: 10,
    }"
    maxlength="1000"
    show-count
    style="margin-top: 10px"
    :input-props="{ spellcheck: false }"
  />
  <n-button type="primary" @click="reply()" style="margin-top: 10px">
    发布
  </n-button>
</template>
