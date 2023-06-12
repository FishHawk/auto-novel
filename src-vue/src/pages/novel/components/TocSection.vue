<script lang="ts" setup>
import { SortFilled } from '@vicons/material';
import { computed, ref } from 'vue';
import { useWindowSize } from '@vueuse/core';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';

const { width } = useWindowSize();
const isDesktop = computed(() => width.value > 600);

const props = defineProps<{
  providerId: string;
  novelId: string;
  toc: WebNovelTocItemDto[];
}>();

const isReverse = ref(false);
const reverseToc = computed(() => props.toc.slice().reverse());
</script>

<template>
  <n-space align="baseline" justify="space-between" style="width: 100">
    <n-h2 prefix="bar">目录</n-h2>
    <n-button @click="isReverse = !isReverse">
      <template #icon>
        <n-icon> <SortFilled /> </n-icon>
      </template>
      {{ isReverse ? '倒序' : '正序' }}
    </n-button>
  </n-space>

  <n-list>
    <n-list-item
      v-for="tocItem in isReverse ? reverseToc : toc"
      style="padding: 0px"
    >
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
