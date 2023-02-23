<script lang="ts" setup>
import { BookPageDto, stateToFileList } from '../api/api_novel';
import { ResultState } from '../api/result';
import { buildMetadataUrl } from '../data/provider';

defineProps<{
  currentPage: number;
  pageNumber: number;
  bookPage: ResultState<BookPageDto>;
}>();
</script>

<template>
  <div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <n-divider />
    <div v-if="bookPage?.ok">
      <div v-for="item in bookPage.value.items">
        <n-h3 class="title" style="margin-bottom: 4px">
          <n-a
            :href="`/novel/${item.providerId}/${item.bookId}`"
            target="_blank"
          >
            {{ item.titleJp }}
          </n-a>
        </n-h3>
        <div>{{ item.titleZh }}</div>
        <n-a
          :href="buildMetadataUrl(item.providerId, item.bookId)"
          target="_blank"
        >
          {{ buildMetadataUrl(item.providerId, item.bookId) }}
        </n-a>
        <div v-for="extraLine in item.extra.split('\n')" style="color: #666">
          {{ extraLine }}
        </div>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
  </div>
</template>
