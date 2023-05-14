<script lang="ts" setup>
import { BookListPageDto, BookRankPageDto } from '@/data/api/api_web_novel';
import { buildMetadataUrl } from '@/data/provider';

defineProps<{
  page: BookListPageDto | BookRankPageDto;
}>();
</script>

<template>
  <div v-for="item in page.items">
    <n-h3 class="title" style="margin-bottom: 4px">
      <n-a :href="`/novel/${item.providerId}/${item.bookId}`" target="_blank">
        {{ item.titleJp }}
      </n-a>
    </n-h3>
    <div>{{ item.titleZh }}</div>
    <n-a :href="buildMetadataUrl(item.providerId, item.bookId)" target="_blank">
      {{ buildMetadataUrl(item.providerId, item.bookId) }}
    </n-a>
    <template v-if="'extra' in item">
      <div v-for="extraLine in item.extra.split('\n')" style="color: #666">
        {{ extraLine }}
      </div>
    </template>
    <template v-else>
      <div style="color: #666">
        总计{{ item.total }} / 百度{{ item.countBaidu }} / 有道{{
          item.countYoudao
        }}
      </div>
    </template>
    <n-divider />
  </div>
</template>
