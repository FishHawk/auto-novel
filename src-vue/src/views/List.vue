<script lang="ts" setup>
import { ref, watch } from 'vue';

import { ResultRef } from '../api/result';
import ApiNovel, { BookPageDto, stateToFileList } from '../api/api_novel';
import { buildMetadataUrl } from '../data/provider';

const currentPage = ref(1);
const total = ref(1);
const bookPage: ResultRef<BookPageDto> = ref();

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await ApiNovel.list(currentPage.value - 1);
  if (currentPage.value == page) {
    bookPage.value = result;
    if (result.ok) {
      total.value = result.value.total;
    }
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <div class="content">
    <n-pagination
      v-model:page="currentPage"
      :page-count="total / 10"
      show-quick-jumper
    />
    <n-divider />
    <div class="list" v-if="bookPage?.ok">
      <div v-for="item in bookPage.value.items">
        <n-h2 class="title">
          <n-a
            :href="`/novel/${item.providerId}/${item.bookId}`"
            target="_blank"
          >
            {{ item.titleJp }}
          </n-a>
        </n-h2>
        <div>{{ item.titleZh }}</div>
        <n-a
          :href="buildMetadataUrl(item.providerId, item.bookId)"
          target="_blank"
        >
          {{ buildMetadataUrl(item.providerId, item.bookId) }}
        </n-a>

        <n-space
          v-for="group in stateToFileList(
            item.providerId,
            item.bookId,
            item.state
          )"
        >
          <span>{{ group.label }}</span>
          <n-space spacer="|">
            <n-a v-for="file in group.files" :href="file.url" target="_blank">
              {{ file.label }}
            </n-a>
          </n-space>
        </n-space>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="total / 10"
      show-quick-jumper
    />
  </div>
</template>

<style>
.list {
  min-height: 600px;
}
</style>
