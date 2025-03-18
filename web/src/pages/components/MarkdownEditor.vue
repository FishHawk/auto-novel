<script setup lang="ts">
import { Locator } from '@/data';
import {
  FormatBoldOutlined,
  FormatItalicOutlined,
  DeleteOutlineFilled,
} from '@vicons/material';
import { DropdownOption, NIcon } from 'naive-ui';

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
const draftOptions = ref<DropdownOption[]>([]);

// 更新草稿的下拉列表
watch(
  drafts,
  () => {
    draftOptions.value = [];
    for (const draft of drafts.value) {
      draftOptions.value.push({
        label: draft.createdAt.toLocaleString('zh-CN'),
        key: draft.text,
      });
    }
    draftOptions.value.push(
      ...[
        {
          type: 'divider',
        },
        {
          label: '删除',
          key: 'deleteAllDrafts',
          icon: () => h(NIcon, null, { default: () => h(DeleteOutlineFilled) }),
        },
      ],
    );
  },
  { immediate: true },
);

const handleSelectDraft = (key: string) => {
  if (key === 'deleteAllDrafts') {
    clearDrafts();
  } else {
    restoreDraft(key);
  }
};

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

const processSelection = (
  select_func: ((str: string) => string) | null,
  insert_str: string = '',
) => {
  const selectedText = window.getSelection()?.toString();
  if (selectedText && select_func) {
    document.execCommand('insertText', false, `${select_func(selectedText)}`);
  } else {
    document.execCommand('insertText', false, `${selectedText}${insert_str}`);
  }
};

const toolbarButtons: {
  icon?: typeof FormatBoldOutlined;
  label?: string;
  action: () => void;
}[] = [
  {
    icon: FormatBoldOutlined,
    action: () => {
      processSelection((str: string) => `**${str}**`, '**粗体**');
    },
  },
  {
    icon: FormatItalicOutlined,
    action: () => {
      processSelection((str: string) => `*${str}*`, '*斜体*');
    },
  },
  {
    label: '剧透',
    action: () => {
      processSelection((str: string) => `!!${str}!!`, '!!剧透!!');
    },
  },
  {
    label: '折叠',
    action: () => {
      processSelection(
        (str: string) => `::: details 详情\n${str}\n:::\n`,
        '::: details 详情\n此文本将被隐藏\n:::\n',
      );
    },
  },
];
</script>

<template>
  <div>
    <!-- 工具栏 -->
    <n-card
      :bordered="false"
      size="small"
      header-style="padding-bottom: 0;"
      content-style="padding-top: 0;"
    >
      <template #header>
        <n-flex justify="start" style="margin-bottom: 8px">
          <!-- 工具按钮 -->
          <n-button
            v-for="button in toolbarButtons"
            quaternary
            @click="button.action"
          >
            <template v-if="button.icon && button.label" #icon>
              <n-icon :component="button.icon" />
            </template>
            {{ button.label }}
            <template v-if="button.icon && !button.label">
              <n-icon size="20px" :component="button.icon" />
            </template>
          </n-button>
        </n-flex>
      </template>
      <template #header-extra>
        <n-flex justify="start" style="margin-bottom: 8px">
          <!-- 草稿箱 -->
          <n-dropdown
            :options="draftOptions"
            trigger="click"
            @select="handleSelectDraft"
          >
            <n-button quaternary>
              <template #icon>
                <n-badge
                  :value="drafts.length"
                  color="rgba(255, 0, 0, 0.3)"
                  show-zero
                >
                  <n-text style="font-size: medium">草稿</n-text>
                </n-badge>
              </template>
            </n-button>
          </n-dropdown>
        </n-flex>
      </template>

      <!-- 编辑框 -->
      <n-input
        v-bind="$attrs"
        v-model:value="value"
        type="textarea"
        show-count
        :input-props="{ spellcheck: false }"
        @input="saveDraft"
      />
    </n-card>
  </div>
</template>
