<script lang="ts" setup>
import { Locator } from '@/data';

const props = defineProps<{
  label?: string;
  icon?: Component;
  requireLogin?: boolean;
  onAction?: (e: MouseEvent) => unknown;
}>();

const message = useMessage();

const { whoami } = Locator.authRepository();

const running = ref(false);

const onClick = async (e: MouseEvent) => {
  if (!props.onAction) return;

  if (props.requireLogin === true && !whoami.value.isSignedIn) {
    message.info('请先登录');
    return;
  }
  if (running.value) {
    message.warning('处理中...');
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
    <template v-if="icon && label" #icon>
      <n-icon :component="icon" />
    </template>
    {{ label }}
    <template v-if="icon && !label">
      <n-icon :component="icon" />
    </template>
  </n-button>
</template>
