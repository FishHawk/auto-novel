<script lang="ts" setup>
import { Toast } from '@novelia/web-kit';

import { useWhoamiStore } from '@/stores';

const props = defineProps<{
  label?: string;
  icon?: Component;
  requireLogin?: boolean;
  onAction?: (e: MouseEvent) => unknown;
}>();

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const running = ref(false);

const onClick = async (e: MouseEvent) => {
  if (!props.onAction) return;

  if (props.requireLogin === true && !whoami.value.isSignedIn) {
    Toast.info('请先登录');
    return;
  }
  if (running.value) {
    Toast.warning('处理中...');
    return;
  }
  const ret = props.onAction(e);
  if (ret instanceof Promise) {
    try {
      running.value = true;
      await ret;
    } finally {
      running.value = false;
    }
  }
};
</script>

<template>
  <n-button round :loading="running" @click="onClick">
    <!-- 图标统一放在 icon 插槽，loading 时由加载动画替换，不会与图标并排撑出按钮 -->
    <template v-if="icon" #icon>
      <n-icon :component="icon" />
    </template>
    <template v-if="label">{{ label }}</template>
  </n-button>
</template>
