<script lang="ts" setup>
defineProps<{ title: string }>();
</script>

<template>
  <header>
    <n-space align="baseline" justify="space-between" style="width: 100">
      <n-h2 prefix="bar">{{ title }}</n-h2>
      <slot />
    </n-space>
  </header>
</template>
