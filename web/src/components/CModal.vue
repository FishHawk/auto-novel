<script lang="ts" setup>
import { XDialog } from '@novelia/web-kit';
import { useId, type StyleValue } from 'vue';

// XDialog owns interaction and focus; this component only supplies app layout.
defineOptions({ inheritAttrs: false });

const props = withDefaults(
  defineProps<{
    title?: string;
    contentStyle?: StyleValue;
    contentMaxHeight?: string;
  }>(),
  {
    title: undefined,
    contentStyle: undefined,
    contentMaxHeight: '60dvh',
  },
);
const open = defineModel<boolean>('open', { default: false });
const attrs = useAttrs();
const slots = useSlots();
const headerId = useId();

// XDialog 的新版标题栏默认可见；这里沿用自定义布局，标题在下方单独渲染。
// 标题栏内容负责对话框的可访问命名，没有标题时退回 aria-label。
const dialogAttrs = () => {
  const label = props.title || attrs['aria-label'] || '对话框';
  return {
    ...attrs,
    'aria-label': label,
    ...(!attrs['aria-labelledby'] && (props.title !== undefined || slots.header)
      ? { 'aria-labelledby': headerId }
      : {}),
  };
};
</script>

<template>
  <XDialog
    v-bind="dialogAttrs()"
    v-model:open="open"
    :scrollable="false"
    body-class="p-0"
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
      class="flex min-h-0 flex-1 flex-col px-6 pb-5"
      :class="{ 'pt-5': title === undefined && !$slots.header }"
      :style="contentStyle"
    >
      <div v-if="$slots['header-extra']" class="shrink-0">
        <slot name="header-extra" />
      </div>
      <div
        class="min-h-0 flex-1 overflow-auto pr-4"
        :style="{ maxHeight: contentMaxHeight }"
      >
        <slot />
      </div>
    </div>

    <footer
      v-if="$slots.action"
      class="flex shrink-0 justify-end gap-3 px-6 pt-4 pb-5"
    >
      <slot name="action" />
    </footer>
  </XDialog>
</template>
