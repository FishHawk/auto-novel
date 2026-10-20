<script setup lang="ts">
import { XMarkdownEditor, XMarkdownEditorToolButton } from '@novelia/web-kit';
import { useEventListener } from '@vueuse/core';
import {
  HelpOutlineOutlined,
  MenuOpenOutlined,
  StarOutlineFilled,
  WarningAmberOutlined,
} from '@vicons/material';
import type { DropdownOption } from 'naive-ui';

import { useDraftStore } from '@/stores';

defineOptions({ inheritAttrs: false });
const props = withDefaults(
  defineProps<{
    mode: 'article' | 'comment';
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
      :tools="['bold', 'italic', 'strike', 'link']"
      @update:model-value="saveDraft"
    >
      <template #preview="{ source }">
        <MarkdownView :mode="mode" :source="source || '没有可预览的内容'" />
      </template>
      <template #toolbar="{ wrapSelection, insertBlock, disabled }">
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
          label="剧透"
          :disabled="disabled"
          @click="wrapSelection('!!', '!!', '剧透')"
        >
          <WarningAmberOutlined class="size-4" aria-hidden="true" />
        </XMarkdownEditorToolButton>
        <XMarkdownEditorToolButton
          label="评分"
          :disabled="disabled"
          @click="insertBlock('::: star 5')"
        >
          <StarOutlineFilled class="size-4" aria-hidden="true" />
        </XMarkdownEditorToolButton>
        <XMarkdownEditorToolButton
          label="折叠"
          :disabled="disabled"
          @click="insertBlock('::: details 点击展开\n', '\n:::', '折叠内容')"
        >
          <MenuOpenOutlined class="size-4" aria-hidden="true" />
        </XMarkdownEditorToolButton>
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
