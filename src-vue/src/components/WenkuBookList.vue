<script lang="ts" setup>
import { ref, watch } from 'vue';

import { Result, ResultState } from '../data/api/result';
import { WenkuListPageDto } from '../data/api/api_wenku_novel';
import { buildMetadataUrl } from '../data/provider';

type Loader = (
  page: number,
  query: string,
  selected: number[]
) => Promise<Result<WenkuListPageDto>>;

const props = defineProps<{
  search: boolean;
  options: { label: string; tags: string[] }[];
  loader: Loader;
}>();

const currentPage = ref(1);
const pageNumber = ref(1);
const bookPage = ref<ResultState<WenkuListPageDto>>();

const filters = ref({
  query: '',
  selected: Array(props.options.length).fill(0),
});

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await props.loader(
    page,
    filters.value.query,
    filters.value.selected
  );
  if (currentPage.value == page) {
    bookPage.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

async function refresh() {
  currentPage.value = 1;
  await loadPage(1);
}

watch(filters, (_) => refresh(), { deep: true });
watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <ListFilters :search="search" :options="options" v-model:filters="filters" />

  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
    style="margin-top: 20px"
  />
  <n-divider />
  <div v-if="bookPage?.ok">
    <n-grid cols="2 400:4 600:6">
      <n-grid-item v-for="item in bookPage.value.items">
        <img :src="item.cover" />
        <div>{{ item.title }}</div>
      </n-grid-item>
    </n-grid>

    <n-empty v-if="bookPage.value.items.length === 0" description="空列表" />
  </div>
  <n-result
    v-if="bookPage && !bookPage.ok"
    status="error"
    title="加载错误"
    :description="bookPage.error.message"
  />
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
