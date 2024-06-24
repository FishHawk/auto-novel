<script lang="ts" setup>
import coverPlaceholder from '@/image/cover_placeholder.png';

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
      style="
        width: 100%;
        aspect-ratio: 1 / 1.5;
        object-fit: cover;
        border-radius: 2px;
      "
      loading="lazy"
    />

    <n-text tag="div" style="height: 2.5em; padding: 8px" class="text-2line">
      <slot name="prefix" />
      <n-text>
        {{ title }}
      </n-text>
    </n-text>
  </n-flex>
</template>
