<script lang="ts" setup>
import { Toast, XConfirmDialog } from '@novelia/web-kit';

// Presentation attrs belong to the original Naive button, not the dialog.
defineOptions({ inheritAttrs: false });
const props = defineProps<{
  hint: string;
  label?: string;
  icon?: Component;
  onAction?: (e: MouseEvent) => unknown;
}>();

const attrs = useAttrs();
const open = ref(false);
const running = ref(false);
const triggerEvent = shallowRef<MouseEvent>();

const openConfirmation = (event: MouseEvent) => {
  if (running.value) return;
  // XConfirmDialog's confirm event has no payload. Keep the real trigger
  // MouseEvent for legacy callbacks instead of passing undefined/an Event.
  triggerEvent.value = event;
  open.value = true;
};
const updateOpen = (value: boolean) => {
  // XConfirmDialog 只通知确认，由调用方在操作成功后关闭；loading 期间它自身
  // 也会阻止关闭，这里再兜底 Escape／遮罩等交互。
  if (!running.value) open.value = value;
};
const confirm = async () => {
  if (running.value || !triggerEvent.value) return;
  running.value = true;
  try {
    const result = await props.onAction?.(triggerEvent.value);
    // Like Naive's positive-click, returning false leaves confirmation open.
    if (result !== false) open.value = false;
  } catch (error) {
    // Leave the confirmation open for retry, and consume the rejection.
    Toast.error(error instanceof Error ? error.message : String(error));
  } finally {
    running.value = false;
  }
};
</script>

<template>
  <n-button
    round
    v-bind="$attrs"
    :loading="running || !!attrs.loading"
    @click="openConfirmation"
  >
    <template v-if="icon && label" #icon>
      <n-icon :component="icon" />
    </template>
    {{ label }}
    <template v-if="icon && !label">
      <n-icon :component="icon" />
    </template>
  </n-button>
  <XConfirmDialog
    :open="open"
    :title="label ? `确认${label}` : '确认操作'"
    :description="hint"
    confirm-label="确定"
    :danger="attrs.type === 'error'"
    :loading="running"
    @update:open="updateOpen"
    @confirm="confirm"
  />
</template>
