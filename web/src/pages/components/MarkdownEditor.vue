<script setup lang="ts">
import { Locator } from '@/data';
import {
  FormatBoldOutlined,
  FormatItalicOutlined,
  DeleteOutlineFilled,
  StarOutlineFilled,
  StrikethroughSOutlined,
} from '@vicons/material';
import { DropdownOption, NIcon } from 'naive-ui';
import MarkdownEdiorToolbarStar from './MarkdownEdiorToolbarStar.vue';

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

/** dropdown的options中的icon需要的值为一个渲染icon的函数 */
const renderIcon = (icon: typeof FormatBoldOutlined) => () =>
  h(NIcon, null, { default: () => h(icon) });

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
          icon: renderIcon(DeleteOutlineFilled),
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

const storedRange = ref<{
  anchorNode?: Node | null;
  start: number;
  end: number;
}>({ start: 0, end: 0 });

const getTextArea = (anchorNode?: Node | null) => {
  if (anchorNode) {
    const targetElement =
      anchorNode instanceof HTMLElement
        ? anchorNode
        : anchorNode?.parentElement;
    return targetElement?.parentElement?.querySelector('textarea');
  }
};
/** 储存选中的文本区域。这个方法会在每次点击工具栏button或dropdown时调用，以防止因改变焦点失去选区而无法插入或替换文本 */
const storeSelection = () => {
  const anchorNode = window.getSelection()?.anchorNode;
  const textarea = getTextArea(anchorNode);
  if (textarea) {
    storedRange.value = {
      anchorNode: anchorNode,
      start: textarea.selectionStart,
      end: textarea.selectionEnd,
    };
  }
};
const restoreSelection = () => {
  const textarea = getTextArea(storedRange.value.anchorNode);
  if (textarea) {
    textarea.focus();
    textarea.setSelectionRange(storedRange.value.start, storedRange.value.end);
    // 选中缓存中的文本区域后，将缓存清除。不然线先点击其他地方让文本区失去选择，紧接再着点击其它按钮时会使用这个缓存区域
    storedRange.value.anchorNode = null;
  }
};
/* 处理文本，两种情况：1. 处理选中的文本，2. 未选中时插入样例文本 */
const processSelection = (
  select_func?: ((str: string) => string) | null,
  insert_str: string = '',
) => {
  // 恢复选中区
  restoreSelection();
  const selectedText = window.getSelection()?.toString() || '';
  const result =
    select_func && selectedText
      ? select_func(selectedText)
      : selectedText + insert_str;
  document.execCommand('insertText', false, result);
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
    icon: StrikethroughSOutlined,
    action: () => {
      processSelection((str: string) => `~~${str}~~`, '~~删除线~~');
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

const showToolBarStar = ref(false);

const toolbarDropdowns: {
  icon?: typeof FormatBoldOutlined;
  label?: string;
  show?: Ref<boolean, boolean>;
  options: DropdownOption[];
  handleSelect?: () => void;
}[] = [
  {
    icon: StarOutlineFilled,
    show: showToolBarStar,
    options: [
      {
        type: 'render',
        render: () =>
          h(MarkdownEdiorToolbarStar, {
            onClick: (star) => {
              showToolBarStar.value = false;
              processSelection(null, `::: star ${star}\n`);
            },
          }),
      },
    ],
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
        <n-flex
          justify="start"
          style="margin-bottom: 8px; flex-wrap: nowrap; gap: 0px"
        >
          <!-- 工具按钮，点击时会 storeSelection -->
          <n-button
            v-for="button in toolbarButtons"
            quaternary
            size="small"
            @click="
              () => {
                storeSelection();
                button.action();
              }
            "
          >
            <template v-if="button.icon && button.label" #icon>
              <n-icon :component="button.icon" />
            </template>
            {{ button.label }}
            <template v-if="button.icon && !button.label">
              <n-icon size="20px" :component="button.icon" />
            </template>
          </n-button>
          <!-- 工具下拉菜单按钮，点击时会 storeSelection，失焦时或选中下拉菜单选项时会 restoreSelection 并隐藏下拉菜单 -->
          <n-dropdown
            v-for="dropdown in toolbarDropdowns"
            trigger="click"
            :options="dropdown.options"
            :show="dropdown.show?.value"
            @update-show="
              (v) => {
                if (dropdown.show) dropdown.show.value = v;
              }
            "
            @clickoutside="
              () => {
                restoreSelection();
                if (dropdown.show) dropdown.show.value = false;
              }
            "
            @select="
              () => {
                restoreSelection();
                if (dropdown.show) dropdown.show.value = false;
                if (dropdown.handleSelect) dropdown.handleSelect();
              }
            "
          >
            <n-button quaternary size="small" @click="storeSelection">
              <template v-if="dropdown.icon && dropdown.label" #icon>
                <n-icon :component="dropdown.icon" />
              </template>
              {{ dropdown.label }}
              <template v-if="dropdown.icon && !dropdown.label">
                <n-icon size="20px" :component="dropdown.icon" />
              </template>
            </n-button>
          </n-dropdown>
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
            <n-button size="small" quaternary>
              <n-badge
                :value="drafts.length"
                color="rgba(255, 0, 0, 0.3)"
                show-zero
              >
                草稿
              </n-badge>
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
