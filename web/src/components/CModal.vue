<script lang="ts" setup>
import { XDialog } from '@novelia/web-kit';
import { useId, type StyleValue } from 'vue';

// XDialog owns interaction and focus; this component only supplies app layout.
defineOptions({ inheritAttrs: false });

withDefaults(
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

// Custom visible headers label the dialog unless the caller supplies a label.
const dialogAttrs = () => ({
  ...attrs,
  ...(!attrs['aria-labelledby'] && (attrs['aria-label'] || slots.header)
    ? { 'aria-labelledby': attrs['aria-label'] ? undefined : headerId }
    : {}),
});
</script>

<template>
  <XDialog
    v-bind="dialogAttrs()"
    v-model:open="open"
    :title="title || '对话框'"
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
