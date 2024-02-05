<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import { useUserDataStore } from '@/data/stores/user_data';

const { onAsyncClick } = defineProps<{
  onAsyncClick: () => Promise<any>;
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
  <n-button round @click="onClick()">
    <template #icon>
      <slot name="icon" />
    </template>
    <slot />
  </n-button>
</template>
