<script lang="ts" setup>
defineProps<{ title: string; align?: 'baseline' | 'center' }>();
</script>

<template>
  <n-flex :align="align ?? 'baseline'" :wrap="false">
    <n-text depth="3" style="white-space: nowrap; font-size: 12px">{{
      title
    }}</n-text>
    <slot />
  </n-flex>
</template>
