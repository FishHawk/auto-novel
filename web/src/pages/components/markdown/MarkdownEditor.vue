<script setup lang="ts">
import { Locator } from '@/data';

import { useIsWideScreen } from '@/pages/util';

const props = defineProps<{
  mode: 'article' | 'comment';
  draftId?: string;
  autosize?:
    | boolean
    | {
        minRows?: number;
        maxRows?: number;
      };
}>();

const value = defineModel<string>('value', { required: true });

const isWideScreen = useIsWideScreen(620);

const showEditorToolbar = ref(true);
const onTabUpdate = (value: number) => {
  showEditorToolbar.value = value === 0;
};

// ==============================
// 草稿
// ==============================

const createdAt = Date.now();

const getDrafts = () => {
  if (props.draftId === undefined) return [];
  return Locator.draftRepository().getDraft(props.draftId);
};

const saveDraft = (text: string) => {
  if (props.draftId && text.trim() !== '') {
    Locator.draftRepository().addDraft(props.draftId, createdAt, text);
  }
};

const drafts = ref(getDrafts());

const clearDraft = () => {
  if (!props.draftId) return;
  Locator.draftRepository().removeDraft(props.draftId);
  drafts.value = getDrafts();
};

const elEditor = useTemplateRef('editor');
</script>

<template>
  <n-el tag="div" class="markdown-input">
    <n-tabs
      ref="tab"
      class="tabs"
      type="card"
      size="small"
      @update:value="onTabUpdate"
    >
      <template v-if="showEditorToolbar && isWideScreen" #suffix>
        <MarkdownToolbar
          :el-textarea="elEditor?.textareaElRef ?? undefined"
          :drafts="drafts"
          @clear-draft="clearDraft"
        />
      </template>
      <n-tab-pane tab="编辑" :name="0">
        <n-flex
          v-if="!isWideScreen"
          :size="0"
          align="center"
          style="margin-left: 8px; margin-bottom: 8px"
        >
          <MarkdownToolbar
            :el-textarea="elEditor?.textareaElRef ?? undefined"
            :drafts="drafts"
            @clear-draft="clearDraft"
          />
        </n-flex>

        <div style="padding: 0 8px 8px">
          <n-input
            ref="editor"
            v-bind="$attrs"
            v-model:value="value"
            type="textarea"
            show-count
            :input-props="{ spellcheck: false }"
            @input="saveDraft"
            :autosize="autosize || { minRows: 8 }"
          />
        </div>
      </n-tab-pane>
      <n-tab-pane tab="预览" :name="1">
        <div style="padding: 0px 16px">
          <MarkdownView
            :mode="mode"
            :source="(value as string) || '没有可预览的内容'"
          />
        </div>
      </n-tab-pane>
    </n-tabs>
  </n-el>
</template>

<style>
.markdown-input {
  border: 1px solid var(--border-color);
  border-radius: 4px;
  overflow: hidden;
}

.markdown-input .tabs .n-tabs-nav {
  --n-tab-gap: 0;
  background-color: var(--tab-color);
  margin: -1px 0 0 -1px;
}

.markdown-input .tabs .n-tabs-tab:not(.n-tabs-tab--active) {
  --n-tab-color: transparent;
  border-top-color: transparent !important;
  border-left-color: transparent !important;
  border-right-color: transparent !important;
}

.markdown-input .tabs .n-tabs-tab--active {
  background-color: var(--body-color) !important;
  border-bottom-color: var(--body-color) !important;
}
</style>
