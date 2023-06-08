<script lang="ts" setup>
import { computed } from 'vue';
import { useWindowSize } from '@vueuse/core';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';

const { width } = useWindowSize();
const isDesktop = computed(() => width.value > 600);

defineProps<{
  providerId: string;
  novelId: string;
  toc: WebNovelTocItemDto[];
}>();
</script>

<template>
  <n-h2 prefix="bar">目录</n-h2>
  <n-list>
    <n-list-item v-for="tocItem in toc" style="padding: 0px">
      <n-a
        v-if="tocItem.chapterId"
        :href="`/novel/${providerId}/${novelId}/${tocItem.chapterId}`"
      >
        <TocItem :item="tocItem" :desktop="isDesktop" />
      </n-a>
      <TocItem v-else :item="tocItem" :desktop="isDesktop" />
    </n-list-item>
  </n-list>
</template>
