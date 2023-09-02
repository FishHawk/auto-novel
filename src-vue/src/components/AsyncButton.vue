<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';

import { useAuthInfoStore } from '@/data/stores/authInfo';

const { onAsyncClick } = defineProps<{
  onAsyncClick: () => Promise<void>;
}>();

const authInfoStore = useAuthInfoStore();
const message = useMessage();

const running = ref(false);

async function onClick() {
  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  if (running.value) return;
  running.value = true;
  await onAsyncClick();
  running.value = false;
}
</script>

<template>
  <n-button @click="onClick()">
    <template #icon>
      <slot name="icon" />
    </template>
    <slot />
  </n-button>
</template>
