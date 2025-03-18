<script setup lang="ts">
import avaterUrl from '@/image/avater.jpg';

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
          <markdown-editor
            v-model:value="value"
            v-bind="$attrs"
            :draft-id="draftId"
            :created-at="createdAt"
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
