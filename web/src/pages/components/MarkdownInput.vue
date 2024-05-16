<script setup lang="ts">
const vars = useThemeVars();
</script>

<template>
  <div class="markdown-input">
    <n-tabs class="tabs" type="card" size="small">
      <n-tab-pane tab="编辑" :name="0">
        <div style="padding: 0 8px 8px">
          <n-input v-bind="$attrs" type="textarea" />
        </div>
      </n-tab-pane>
      <n-tab-pane tab="预览" :name="1">
        <div style="padding: 8px 16px 16px">
          <markdown :source="($attrs.value as string) || '没有可预览的内容'" />
        </div>
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<style>
.markdown-input {
  border: 1px solid v-bind('vars.borderColor');
  border-radius: 4px;
  overflow: hidden;
}

.markdown-input .tabs .n-tabs-nav {
  --n-tab-gap: 0;
  background-color: v-bind('vars.tabColor');
  margin: -1px 0 0 -1px;
}

.markdown-input .tabs .n-tabs-tab:not(.n-tabs-tab--active) {
  --n-tab-color: transparent;
  border-top-color: transparent !important;
  border-left-color: transparent !important;
  border-right-color: transparent !important;
}

.markdown-input .tabs .n-tabs-tab--active {
  background-color: v-bind('vars.bodyColor') !important;
  border-bottom-color: v-bind('vars.bodyColor') !important;
}
</style>
