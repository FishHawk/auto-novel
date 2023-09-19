<script lang="ts" setup>
withDefaults(defineProps<{ title: string; description: string }>(), {
  title: '',
  description: '',
});
</script>

<template>
  <n-space justify="space-between" :wrap="false">
    <div>
      <n-text>
        <b>{{ title }}</b>
      </n-text>
      <br />
      <n-text style="font-size: 12px">
        {{ description }}
      </n-text>
      <div style="margin-top: 4px">
        <slot />
      </div>
    </div>
    <n-space align="center" style="height: 100%">
      <slot name="suffix" />
    </n-space>
  </n-space>
</template>
