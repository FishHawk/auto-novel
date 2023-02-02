<script lang="ts" setup>
import { ref, watch } from 'vue';

import { ResultState } from '../api/result';
import ApiPatch, { BookPatchPageDto } from '../api/api_patch';
import { buildMetadataUrl } from '../data/provider';

const currentPage = ref(1);
const total = ref(1);
const bookPage = ref<ResultState<BookPatchPageDto>>();

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await ApiPatch.list(currentPage.value - 1);
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
      :page-count="Math.floor(total / 10)"
      show-quick-jumper
    />
    <n-divider />
    <div v-if="bookPage?.ok">
      <div v-for="item in bookPage.value.items">
        <n-h2 class="title">
          <n-a
            :href="`/patch/${item.providerId}/${item.bookId}`"
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
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      show-quick-jumper
    />
  </div>
</template>
