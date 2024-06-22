<script lang="ts" setup>
import { Locator } from '@/data';

const props = defineProps<{
  label?: string;
  icon?: Component;
  requireLogin?: boolean;
  onAction?: (e: MouseEvent) => any;
}>();

const message = useMessage();

const running = ref(false);

const onClick = async (e: MouseEvent) => {
  if (!props.onAction) return;

  if (
    props.requireLogin === true &&
    !Locator.authRepository().isSignedIn.value
  ) {
    message.info('请先登录');
    return;
  }
  if (running.value) {
    message.warning('处理中...');
    return;
  }
  running.value = true;
  await props.onAction(e);
  running.value = false;
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
