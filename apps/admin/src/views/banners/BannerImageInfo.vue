<script setup lang="ts">
import type { BannerImage } from './bannerImage';
defineProps<{ image: BannerImage }>();
</script>

<template>
  <p class="hint">
    {{ image.file.name }} · {{ (image.file.size / 1024).toFixed(1) }} KiB · 原始
    {{ image.width }} × {{ image.height }} px
  </p>
</template>
