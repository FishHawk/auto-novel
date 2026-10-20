<script setup lang="ts">
import { XMarkdownEditor, XMarkdownEditorToolButton } from '@novelia/web-kit';
import { useEventListener } from '@vueuse/core';
import { HelpOutlineOutlined } from '@vicons/material';
import type { DropdownOption } from 'naive-ui';

import { useDraftStore } from '@/stores';

import { markdownByMode, type MarkdownMode } from './markdown';

defineOptions({ inheritAttrs: false });
const props = withDefaults(
  defineProps<{
    mode: MarkdownMode;
    draftId?: string;
    rows?: number;
    maxlength?: number;
  }>(),
  { draftId: undefined, rows: 8, maxlength: undefined },
);
const value = defineModel<string>('value', { required: true });

useEventListener(window, 'beforeunload', (event) => {
  if (value.value.trim()) {
    event.preventDefault();
    event.returnValue = '有未保存的编辑，确定要离开吗？';
  }
});

const createdAt = Date.now();
const draftStore = useDraftStore();
const getDrafts = () =>
  props.draftId === undefined ? [] : draftStore.getDraft(props.draftId);
const drafts = ref(getDrafts());
const draftOptions = computed<DropdownOption[]>(() => [
  ...drafts.value
    .slice()
    .reverse()
    .map((draft) => ({
      label: draft.createdAt.toLocaleString('zh-CN'),
      key: draft.createdAt.getTime(),
      draftText: draft.text,
    })),
  { type: 'divider' },
  { label: '清空', key: 'clear' },
]);

const saveDraft = (text: string) => {
  if (props.draftId && text.trim()) {
    draftStore.addDraft(props.draftId, createdAt, text);
  }
};
const selectDraft = (key: string | number, option: DropdownOption) => {
  if (key === 'clear') {
    if (!props.draftId) return;
    draftStore.cancelAddDraft();
    draftStore.removeDraft(props.draftId);
    drafts.value = getDrafts();
  } else {
    const text = option.draftText as string;
    value.value = text;
    saveDraft(text);
  }
};
const showGuide = ref(false);
</script>

<template>
  <div>
    <XMarkdownEditor
      v-bind="$attrs"
      v-model="value"
      :rows="rows"
      :maxlength="maxlength"
      :spellcheck="false"
      :markdown="markdownByMode[mode]"
      :tools="[
        'bold',
        'italic',
        'strike',
        'link',
        'spoiler',
        'star',
        'details',
      ]"
      @update:model-value="saveDraft"
    >
      <template #toolbar="{ disabled }">
        <n-dropdown
          v-if="drafts.length"
          :options="draftOptions"
          trigger="click"
          :disabled="disabled"
          @select="selectDraft"
        >
          <XMarkdownEditorToolButton label="历史草稿" :disabled="disabled">
            草稿
          </XMarkdownEditorToolButton>
        </n-dropdown>
        <XMarkdownEditorToolButton
          label="格式帮助"
          :disabled="disabled"
          @click="showGuide = true"
        >
          <HelpOutlineOutlined class="size-4" aria-hidden="true" />
        </XMarkdownEditorToolButton>
      </template>
    </XMarkdownEditor>
    <div class="mt-1 text-right text-xs text-muted">
      {{ value.length }}
      <template v-if="maxlength !== undefined">/ {{ maxlength }}</template>
    </div>
    <MarkdownGuideModal v-model:open="showGuide" />
  </div>
</template>
