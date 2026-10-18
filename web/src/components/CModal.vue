<script lang="ts" setup>
import { XDialog } from '@novelia/web-kit';
import { useWindowSize } from '@vueuse/core';
import { useId, type StyleValue } from 'vue';

// Keep the legacy wrapper API; Naive-only modal/card props must not reach the DOM.
defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    title?: string;
    contentStyle?: StyleValue;
    maxHeightPercentage?: number;
    extraHeight?: number;
    // 内容自身已是滚动容器时开启，跳过外层滚动条，避免嵌套出现两条滚动条
    contentScrollable?: boolean;
    closable?: boolean;
    maskClosable?: boolean;
    closeOnEsc?: boolean;
    autoFocus?: boolean;
  }>(),
  {
    title: undefined,
    contentStyle: undefined,
    maxHeightPercentage: 60,
    extraHeight: 0,
    closable: undefined,
    maskClosable: true,
    closeOnEsc: true,
    autoFocus: false,
  },
);
const show = defineModel<boolean>('show', { default: false });
const emit = defineEmits<{
  afterEnter: [];
  afterLeave: [];
  openAutoFocus: [event: Event];
  closeAutoFocus: [event: Event];
}>();

const attrs = useAttrs();
const slots = useSlots();
const headerId = useId();
const { height } = useWindowSize();
const scrollMaxHeight = computed(
  () =>
    `${Math.max(0, (props.maxHeightPercentage / 100) * height.value - props.extraHeight)}px`,
);

// useAttrs is updated on render, not a reactive dependency of computed().
const dialogAttrs = () => {
  const forwarded = Object.fromEntries(
    Object.entries(attrs).filter(
      ([key]) =>
        ['id', 'class', 'role', 'tabindex'].includes(key) ||
        key.startsWith('aria-') ||
        key.startsWith('data-'),
    ),
  );
  if (!attrs['aria-labelledby']) {
    if (attrs['aria-label']) forwarded['aria-labelledby'] = undefined;
    else if (slots.header) forwarded['aria-labelledby'] = headerId;
  }
  return forwarded;
};

const handleOpenAutoFocus = (event: Event) => {
  if (!props.autoFocus) event.preventDefault();
  emit('openAutoFocus', event);
  // XDialog has no leave/enter transition. Preserve the legacy lifecycle hook
  // after its portalled content has mounted/unmounted (UploadButton uses it).
  nextTick(() => {
    if (show.value) emit('afterEnter');
  });
};
const handleCloseAutoFocus = (event: Event) => {
  emit('closeAutoFocus', event);
  nextTick(() => {
    if (!show.value) emit('afterLeave');
  });
};
</script>

<template>
  <XDialog
    v-bind="dialogAttrs()"
    v-model:open="show"
    :title="title || '对话框'"
    class="c-modal"
    :class="{
      'c-modal-without-close':
        closable === false ||
        (closable === undefined && title === undefined && !$slots.header),
    }"
    :style="[
      {
        display: 'flex',
        flexDirection: 'column',
        width: 'min(600px, calc(100% - 16px))',
        maxWidth: 'calc(100% - 16px)',
        maxHeight: 'calc(100dvh - 2rem)',
        overflow: 'hidden',
        padding: '0',
      },
      attrs.style as StyleValue,
    ]"
    @open-auto-focus="handleOpenAutoFocus"
    @close-auto-focus="handleCloseAutoFocus"
    @escape-key-down="!closeOnEsc && $event.preventDefault()"
    @pointer-down-outside="!maskClosable && $event.preventDefault()"
  >
    <header
      v-if="title !== undefined || $slots.header"
      class="shrink-0 px-6 pt-5 pr-14 pb-4 text-lg font-medium"
    >
      <div :id="headerId">
        <slot name="header">{{ title }}</slot>
      </div>
    </header>

    <div
      class="c-modal-content flex min-h-0 flex-1 flex-col px-6 pb-5"
      :class="{ 'pt-5': title === undefined && !$slots.header }"
      :style="contentStyle"
    >
      <div v-if="$slots['header-extra']" class="shrink-0">
        <slot name="header-extra" />
      </div>
      <div v-if="contentScrollable" class="min-h-0 flex-1 overflow-auto pr-4">
        <slot />
      </div>
      <n-scrollbar
        v-else
        trigger="none"
        class="min-h-0 flex-1"
        :style="{ maxHeight: scrollMaxHeight }"
      >
        <div style="padding-right: 16px">
          <slot />
        </div>
      </n-scrollbar>
    </div>

    <footer v-if="$slots.action" class="shrink-0 px-6 pt-4 pb-5">
      <n-flex justify="end">
        <slot name="action" />
      </n-flex>
    </footer>
  </XDialog>
</template>

<style>
/* XDialog's closable=false also disables Escape and outside-click dismissal.
   The old CModal only hid the button for untitled content, so keep dismissal.
   This class lives on portalled content, outside this component's scope. */
.c-modal.c-modal-without-close > button[aria-label='关闭窗口'] {
  display: none;
}
</style>
