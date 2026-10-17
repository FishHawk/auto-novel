<script setup lang="ts">
import type { BannerImage } from './bannerImage';
defineProps<{ image: BannerImage }>();
</script>

<template>
  <div class="local-image-preview">
    <img
      :src="image.url"
      :alt="image.file.name"
      :style="{ aspectRatio: `${image.width || 1} / ${image.height || 1}` }"
    />
  </div>
  <p class="hint">
    {{ image.file.name }} · {{ (image.file.size / 1024).toFixed(1) }} KiB · 原始
    {{ image.originalWidth }} × {{ image.originalHeight }} px
  </p>
  <div class="image-dimensions">
    <label class="field">
      <span>宽度（px）</span>
      <input
        v-model.number="image.width"
        type="number"
        min="1"
        max="8192"
        step="1"
        required
      />
    </label>
    <label class="field">
      <span>高度（px）</span>
      <input
        v-model.number="image.height"
        type="number"
        min="1"
        max="8192"
        step="1"
        required
      />
    </label>
    <button
      type="button"
      class="text-button"
      @click="
        image.width = image.originalWidth;
        image.height = image.originalHeight;
      "
    >
      恢复原尺寸
    </button>
  </div>
  <p class="hint">
    调整尺寸会缩放为 PNG（GIF 动画仅保留首帧），最终文件仍须小于 5 MiB。
  </p>
</template>
