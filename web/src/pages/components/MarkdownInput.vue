<script setup lang="ts">
import avaterUrl from '@/image/avater.jpg';
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
  autosize?:
    | boolean
    | {
        minRows?: number;
        maxRows?: number;
      };
}>();
const value = defineModel<string>('value', { required: true });

const createdAt = Date.now();

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
  if (props.draftId && createdAt && text.trim() !== '') {
    Locator.draftRepository().addDraft(props.draftId, createdAt, text);
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
    <n-tabs class="tabs" type="card" size="small">
      <template #suffix>
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
      </template>

      <n-tab-pane tab="编辑" :name="0">
        <div style="padding: 0 8px 8px">
          <!-- 编辑框 -->
          <n-input
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
        <div style="padding: 8px 16px 16px">
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
