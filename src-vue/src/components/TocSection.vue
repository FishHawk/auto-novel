<script lang="ts" setup>
import { BookTocItemDto } from '../data/api/api_web_novel';

defineProps<{
  providerId: string;
  bookId: string;
  toc: BookTocItemDto[];
}>();
</script>

<template>
  <n-h2 prefix="bar">目录</n-h2>
  <table style="width: 100%">
    <template v-for="tocItem in toc">
      <n-a
        v-if="tocItem.episodeId"
        :href="`/novel/${providerId}/${bookId}/${tocItem.episodeId}`"
        role="row"
        style="display: table-row"
      >
        <TocItem :item="tocItem" />
      </n-a>
      <tr v-else>
        <TocItem :item="tocItem" />
      </tr>
      <n-divider class="on-desktop" style="width: 200%; margin: 0px" />
      <n-divider class="on-mobile" style="width: 100%; margin: 0px" />
    </template>
  </table>
</template>
