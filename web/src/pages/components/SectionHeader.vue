<script lang="ts" setup>
defineProps<{ title: string }>();
</script>

<template>
  <n-flex
    align="baseline"
    justify="space-between"
    :wrap="false"
    style="width: 100"
  >
    <n-h2 prefix="bar">{{ title }}</n-h2>
    <n-flex :wrap="false">
      <slot />
    </n-flex>
  </n-flex>
</template>
