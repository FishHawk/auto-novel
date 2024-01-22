<script lang="ts" setup>
withDefaults(defineProps<{ title: string; description: string }>(), {
  title: '',
  description: '',
});
</script>

<template>
  <n-flex align="center" justify="space-between" :wrap="false">
    <div>
      <b>{{ title }}</b>
      <br />
      <n-text style="font-size: 12px">
        {{ description }}
      </n-text>
      <div v-if="$slots.default" style="margin-top: 8px">
        <slot />
      </div>
    </div>
    <slot name="suffix" />
  </n-flex>
</template>
