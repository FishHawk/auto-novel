<script lang="ts" setup>
defineProps<{
  src: string;
  title: string;
}>();
</script>

<template>
  <n-card size="small" header-style="padding: 8px;" :bordered="false">
    <template #cover>
      <img
        :src="src"
        alt="cover"
        style="aspect-ratio: 1 / 1.5; object-fit: cover"
      />
    </template>
  </n-card>

  <n-text tag="div" style="height: 2.5em; padding: 8px" class="text-2line">
    {{ title }}
  </n-text>
</template>
