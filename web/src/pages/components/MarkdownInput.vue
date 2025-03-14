<script setup lang="ts">
import { Locator } from '@/data';
import avaterUrl from '@/image/avater.jpg';

const props = defineProps<{
  value: string;
  draftId?: string;
}>();

const emit = defineEmits<{
  'update:value': [string];
}>();

const getDrafts = () => {
  if (props.draftId === undefined) return [];
  return Locator.draftRepository().getDraft(props.draftId);
};
const drafts = getDrafts();

const createdAt = Date.now();

const restoreDraft = (text: string) => {
  emit('update:value', text);
};
const saveDraft = (text: string) => {
  if (props.draftId) {
    Locator.draftRepository().addDraft(props.draftId, createdAt, text);
  }
};

const formatExample: [string, string][] = [
  ['段落之间要有空行', '第一段巴拉巴拉\n\n第二段巴拉巴拉'],
  ['粗体', '**随机文本**'],
  ['斜体', '*随机文本*'],
  ['删除线', '~~随机文本~~'],
  ['分隔线', '---'],
  ['列表', '- 第一项\n- 第二项\n- 第三项\n'],
  ['链接', '[链接名称](https://books.fishhawk.top)'],
  ['网络图片', `![](${avaterUrl})`],
  ['多级标题', '# 一级标题\n\n## 二级标题\n\n### 三级标题'],
];
</script>

<template>
  <n-el tag="div" class="markdown-input">
    <n-tabs class="tabs" type="card" size="small">
      <n-tab-pane tab="编辑" :name="0">
        <div style="padding: 0 8px 8px">
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
              v-for="draft of drafts"
              style="font-size: 12px; margin-left: 16px"
            >
              <n-button text size="tiny" @click="restoreDraft(draft.text)">
                保存于{{ draft.createdAt.toLocaleString('zh-CN') }}
              </n-button>
            </n-text>
          </n-flex>

          <n-input
            v-bind="$attrs"
            type="textarea"
            show-count
            :input-props="{ spellcheck: false }"
            :value="value"
            @update-value="(it) => emit('update:value', it)"
            @input="saveDraft"
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
                <th><b>格式</b></th>
                <th><b>文本</b></th>
                <th><b>预览</b></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="[name, code] of formatExample">
                <td>
                  <b>{{ name }}</b>
                </td>
                <td style="white-space: pre-wrap">{{ code }}</td>
                <td>
                  <Markdown :source="code" />
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
