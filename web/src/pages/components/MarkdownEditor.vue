<script setup lang="ts">
import { Locator } from '@/data';

const props = defineProps<{
  draftId?: string;
  createdAt?: number;
}>();

const value = defineModel<string>('value', { required: true });

const getDrafts = () => {
  if (props.draftId === undefined) return [];
  return Locator.draftRepository().getDraft(props.draftId);
};
const drafts = ref(getDrafts());

const restoreDraft = (text: string) => {
  value.value = text;
};
const saveDraft = (text: string) => {
  if (props.draftId && props.createdAt && text.trim() !== '') {
    Locator.draftRepository().addDraft(props.draftId, props.createdAt, text);
  }
};
const clearDrafts = () => {
  if (props.draftId) {
    Locator.draftRepository().removeDraft(props.draftId);
    // 更新draft
    drafts.value = getDrafts();
  }
};
</script>

<template>
  <div>
    <!-- 草稿提示 -->
    <n-flex
      v-if="drafts.length > 0"
      vertical
      :size="0"
      style="margin-bottom: 8px"
    >
      <n-text type="warning" style="font-size: 12px">
        <b>* 检测到以下未恢复的草稿，点击恢复</b>
      </n-text>
      <n-text
        v-for="draft in drafts"
        style="font-size: 12px; margin-left: 16px"
      >
        <n-button text size="tiny" @click="restoreDraft(draft.text)">
          保存于{{ draft.createdAt.toLocaleString('zh-CN') }}
        </n-button>
      </n-text>
      <n-text style="font-size: 12px">
        <n-button text type="error" size="tiny" @click="clearDrafts">
          清除所有草稿
        </n-button>
      </n-text>
    </n-flex>

    <!-- 编辑框 -->
    <n-input
      v-bind="$attrs"
      v-model:value="value"
      type="textarea"
      show-count
      :input-props="{ spellcheck: false }"
      @input="saveDraft"
    />
  </div>
</template>
