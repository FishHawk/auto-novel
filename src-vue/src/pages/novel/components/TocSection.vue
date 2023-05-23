<script lang="ts" setup>
import { WebNovelTocItemDto } from '@/data/api/api_web_novel';

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
        <TocItem :item="tocItem" />
      </n-a>
      <TocItem v-else :item="tocItem" />
    </n-list-item>
  </n-list>
</template>
