<script lang="ts" setup>
import { ref } from 'vue';
import { useMessage } from 'naive-ui';

import { useUserDataStore } from '@/data/stores/userData';

const { onAsyncClick } = defineProps<{
  onAsyncClick: () => Promise<void>;
}>();

const userData = useUserDataStore();
const message = useMessage();

const running = ref(false);

async function onClick() {
  if (!userData.isLoggedIn) {
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
