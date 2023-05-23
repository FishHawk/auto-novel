<script lang="ts" setup>
import { WebNovelTocItemDto } from '@/data/api/api_web_novel';

defineProps<{
  item: WebNovelTocItemDto;
}>();
</script>

<template>
  <div style="width: 100; display: flex; padding: 6px">
    <span style="flex: 1 1 0">
      {{ item.titleJp }}
      <br />
      <span class="on-mobile" style="color: grey">
        {{ item.titleZh }}
      </span>
    </span>
    <span class="on-desktop" style="color: grey; flex: 1 1 0">
      {{ item.titleZh }}
    </span>
  </div>
</template>
