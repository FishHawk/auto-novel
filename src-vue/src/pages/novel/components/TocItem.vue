<script lang="ts" setup>
import { WebNovelTocItemDto } from '@/data/api/api_web_novel';
import { computed } from 'vue';

const props = defineProps<{
  item: {
    titleJp: string;
    titleZh?: string;
    chapterId?: string;
    createAt?: string;
  };
  desktop: boolean;
}>();
</script>

<template>
  <template v-if="desktop">
    <div style="width: 100; display: flex; padding: 6px">
      <span style="flex: 1 1 0">{{ item.titleJp }}</span>
      <span style="color: grey; flex: 1 1 0">{{ item.titleZh }}</span>
      <span style="color: grey; width: 110px">{{ item.createAt }}</span>
    </div>
  </template>

  <template v-else>
    <div style="width: 100; padding: 6px">
      {{ item.titleJp }}
      <br />
      <span style="color: grey">{{ item.titleZh }}</span>
      <br />
      <span style="color: grey">{{ item.createAt }}</span>
    </div>
  </template>
</template>
