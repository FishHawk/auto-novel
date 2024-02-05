<script lang="ts" setup>
import coverPlaceholder from '@/images/cover_placeholder.png';

defineProps<{
  src: string;
  title: string;
}>();
</script>

<template>
  <n-flex :size="0" vertical>
    <img
      :src="src ? src : coverPlaceholder"
      alt="cover"
      style="aspect-ratio: 1 / 1.5; object-fit: cover; border-radius: 2px"
    />

    <n-text style="height: 2.5em; padding: 8px" class="text-2line">
      {{ title }}
    </n-text>
  </n-flex>
</template>
