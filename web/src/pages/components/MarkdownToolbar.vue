<script setup lang="ts">
import {
  FormatBoldOutlined,
  FormatItalicOutlined,
  HelpOutlineOutlined,
  StarOutlineFilled,
  StrikethroughSOutlined,
  WarningAmberOutlined,
  MenuOpenOutlined,
} from '@vicons/material';
import { DropdownOption, NIcon } from 'naive-ui';
import { Component } from 'vue';

import { Draft } from '@/data/stores/DraftRepository';

const props = defineProps<{
  elTextarea?: HTMLTextAreaElement;
  drafts: Draft[];
}>();

const emit = defineEmits<{
  clearDraft: [];
}>();

// ==============================
// 草稿
// ==============================

const draftOptions = ref<DropdownOption[]>([]);

watch(
  props.drafts,
  (drafts) => {
    const draftOptionsValue: DropdownOption[] = [];
    for (const draft of drafts.slice().reverse()) {
      draftOptionsValue.push({
        label: draft.createdAt.toLocaleString('zh-CN'),
        key: draft.createdAt.getTime(),
        draftText: draft.text,
      });
    }
    draftOptionsValue.push({ type: 'divider' }, { label: '清空', key: '清空' });
    draftOptions.value = draftOptionsValue;
  },
  { immediate: true },
);

const handleSelectDraft = (key: string, option: DropdownOption) => {
  if (key === '清空') {
    emit('clearDraft');
  } else {
    const { elTextarea } = props;
    if (!elTextarea) return;
    elTextarea.value = option.draftText as string;
    elTextarea.dispatchEvent(new Event('input'));
  }
};

// ==============================
// 编辑
// ==============================

const showGuideModal = ref(false);

const processSelection = (
  processer: (str: string) => string,
  fallbackText: string = '',
) => {
  const { elTextarea } = props;
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
  {
    icon: HelpOutlineOutlined,
    label: '格式帮助',
    action: () => (showGuideModal.value = true),
  },
];
</script>

<template>
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

  <markdown-guide-modal v-model:show="showGuideModal" />
</template>
