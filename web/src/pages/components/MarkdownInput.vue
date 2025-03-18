<script setup lang="ts">
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
