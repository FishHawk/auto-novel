<script setup lang="ts">
import avaterUrl from '@/image/avater.jpg';
import {
  FormatBoldOutlined,
  FormatItalicOutlined,
  StarOutlineFilled,
  StrikethroughSOutlined,
  WarningAmberOutlined,
  MenuOpenOutlined,
} from '@vicons/material';
import { DropdownOption, NIcon } from 'naive-ui';
import { Component } from 'vue';

import { Locator } from '@/data';

const props = defineProps<{
  draftId?: string;
  autosize?:
    | boolean
    | {
        minRows?: number;
        maxRows?: number;
      };
}>();

const value = defineModel<string>('value', { required: true });

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
  if (props.draftId && createdAt && text.trim() !== '') {
    Locator.draftRepository().addDraft(props.draftId, createdAt, text);
  }
};

const drafts = ref(getDrafts());
const draftOptions = ref<DropdownOption[]>([]);

watch(
  drafts,
  () => {
    draftOptions.value = [];
    for (const draft of drafts.value.reverse()) {
      draftOptions.value.push({
        label: draft.createdAt.toLocaleString('zh-CN'),
        key: draft.createdAt.getTime(),
        draftText: draft.text,
      });
    }
    draftOptions.value.push(
      ...[{ type: 'divider' }, { label: '清空', key: '清空' }],
    );
  },
  { immediate: true },
);

const handleSelectDraft = (key: string, option: DropdownOption) => {
  if (!props.draftId) return;
  if (key === '清空') {
    Locator.draftRepository().removeDraft(props.draftId);
    drafts.value = getDrafts();
  } else {
    value.value = option.draftText as string;
  }
};

// ==============================
// 编辑
// ==============================

const elEditor = useTemplateRef('editor');

const processSelection = (
  processer: (str: string) => string,
  fallbackText: string = '',
) => {
  const elTextarea = elEditor.value?.textareaElRef;
  if (!elTextarea) return;
  elTextarea.focus();

  const { selectionStart, selectionEnd } = elTextarea;
  const selectedText = elTextarea.value.substring(selectionStart, selectionEnd);
  if (selectedText.length > 0) {
    const processedText = processer(selectedText);
    elTextarea.setRangeText(processedText);
  } else {
    elTextarea.setRangeText(fallbackText);
    elTextarea.selectionStart += fallbackText.length;
  }
  elTextarea.dispatchEvent(new Event('input'));
};

const warpProcesser = (warp: string) => {
  return (text: string) => {
    if (
      text.length >= warp.length * 2 &&
      text.startsWith(warp) &&
      text.endsWith(warp)
    ) {
      return text.substring(warp.length, text.length - warp.length);
    } else {
      return warp + text + warp;
    }
  };
};

const toolbarButtons: {
  icon: Component;
  label: string;
  action: () => void;
}[] = [
  {
    icon: FormatBoldOutlined,
    label: '粗体',
    action: () => processSelection(warpProcesser('**'), '**粗体**'),
  },
  {
    icon: FormatItalicOutlined,
    label: '斜体',
    action: () => processSelection(warpProcesser('*'), '*斜体*'),
  },
  {
    icon: StrikethroughSOutlined,
    label: '删除线',
    action: () => processSelection(warpProcesser('~~'), '~~删除线~~'),
  },
  {
    icon: StarOutlineFilled,
    label: '评分',
    action: () =>
      processSelection((_str: string) => `::: star 5\n`, `::: star 5\n`),
  },
  {
    icon: WarningAmberOutlined,
    label: '剧透',
    action: () => processSelection(warpProcesser('!!'), '!!剧透!!'),
  },
  {
    icon: MenuOpenOutlined,
    label: '折叠',
    action: () =>
      processSelection(
        (str: string) => `\n::: details 点击展开\n${str}\n:::\n`,
        '\n::: details 点击展开\n此文本将被折叠\n:::\n',
      ),
  },
];

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
      <template v-if="showEditorToolbar" #suffix>
        <n-dropdown
          v-if="drafts.length"
          :options="draftOptions"
          trigger="click"
          @select="handleSelectDraft"
        >
          <n-button size="small" quaternary>
            <n-badge :value="drafts.length" dot :offset="[8, -4]">草稿</n-badge>
          </n-button>
        </n-dropdown>

        <n-tooltip v-for="button in toolbarButtons" trigger="hover">
          <template #trigger>
            <n-button
              quaternary
              size="small"
              @mousedown="
                (e) => {
                  button.action();
                  e.preventDefault();
                }
              "
            >
              <template #icon>
                <n-icon :component="button.icon" />
              </template>
            </n-button>
          </template>
          {{ button.label }}
        </n-tooltip>
      </template>

      <n-tab-pane tab="编辑" :name="0">
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
          <markdown :source="(value as string) || '没有可预览的内容'" />
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
                  <Markdown :source="markdownGuide" />
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
