<script setup lang="ts">
import avaterUrl from '@/image/avater.jpg';
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

const isWideScreen = useIsWideScreen(600);

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

const markdownGuide = `# 一级标题
## 二级标题
### 三级标题

使用空行分割段落，不用空行就会像下面这样

**粗体**
*斜体*
~~删除线~~

[链接](https://books.fishhawk.top)

- 无序列表
- 无序列表
  - 加空格可以缩进
  - 加空格可以缩进
- 无序列表

1. 有序列表
1. 有序列表
1. 有序列表

> 引用

下面是分隔线，和文本要用空行隔开

---

| 表格第一列 | 左对齐 | 居中 | 右对齐 |
| - | :- | :-: | -: |
| 文本 | 文本 | 文本 | 文本 |
| 文本 | 文本 | 文本 | 文本 |


下面是图片

![](${avaterUrl})
`;
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
        <markdown-toolbar
          :el-textarea="elEditor?.textareaElRef ?? undefined"
          :drafts="drafts"
          @save-draft="clearDraft"
        />
      </template>
      <n-tab-pane tab="编辑" :name="0">
        <n-flex
          v-if="!isWideScreen"
          :size="0"
          align="center"
          style="margin-left: 8px; margin-bottom: 8px"
        >
          <markdown-toolbar
            :el-textarea="elEditor?.textareaElRef ?? undefined"
            :drafts="drafts"
            @save-draft="clearDraft"
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
          <markdown
            :mode="mode"
            :source="(value as string) || '没有可预览的内容'"
          />
        </div>
      </n-tab-pane>
      <n-tab-pane tab="格式帮助" :name="2">
        <div style="padding: 8px 16px 16px">
          <n-table :bordered="false">
            <thead>
              <tr>
                <th><b>语法</b></th>
                <th><b>预览</b></th>
              </tr>
            </thead>
            <tbody style="vertical-align: top">
              <tr>
                <td style="white-space: pre-wrap">
                  {{ markdownGuide }}
                </td>
                <td>
                  <markdown :mode="mode" :source="markdownGuide" />
                </td>
              </tr>
            </tbody>
          </n-table>
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
