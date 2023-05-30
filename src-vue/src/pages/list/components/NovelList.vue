<script lang="ts">
type NovelPage =
  | { type: 'web'; page: Page<WebNovelOutlineDto> }
  | { type: 'wenku'; page: Page<WenkuNovelOutlineDto> };

export type Loader = (
  page: number,
  query: string,
  selected: number[]
) => Promise<Result<NovelPage>>;
</script>

<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { Result, ResultState } from '@/data/api/result';
import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/page';

const route = useRoute();
const router = useRouter();

const props = defineProps<{
  search: boolean;
  options: { label: string; tags: string[] }[];
  loader: Loader;
}>();

const currentPage = ref(parseInt(route.query.page as string) || 1);
const pageNumber = ref(1);
const novelPage = ref<ResultState<NovelPage>>();

function parseFilters() {
  let query = '';
  if (typeof route.query.query === 'string') {
    query = route.query.query;
  }
  let selected = Array(props.options.length).fill(0);
  if (typeof route.query.selected === 'string') {
    selected[0] = parseInt(route.query.selected) || 0;
  } else if (route.query.selected) {
    route.query.selected.forEach((it, index) => {
      selected[index] = parseInt(it ?? '') || 0;
    });
  }
  return {
    query,
    selected: selected,
  };
}

const filters = ref(parseFilters());

async function loadPage(page: number) {
  novelPage.value = undefined;

  const query: { [key: string]: any } = { page };
  if (props.search) {
    query.query = filters.value.query;
  }
  if (props.options.length > 0) {
    query.selected = filters.value.selected;
  }
  router.replace({ path: route.path, query });

  const result = await props.loader(
    page,
    filters.value.query,
    filters.value.selected
  );
  if (currentPage.value == page) {
    novelPage.value = result;
    if (result.ok) {
      pageNumber.value = result.value.page.pageNumber;
    }
  }
}

async function refresh() {
  currentPage.value = 1;
  await loadPage(1);
}

loadPage(currentPage.value);
watch(filters, (_) => refresh(), { deep: true });
watch(currentPage, (page) => loadPage(page));
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
  <div v-if="novelPage?.ok">
    <NovelListWeb
      v-if="novelPage.value.type === 'web'"
      :items="novelPage.value.page.items"
    />
    <NovelListWenku
      v-else-if="novelPage.value.type === 'wenku'"
      :items="novelPage.value.page.items"
    />

    <n-empty
      v-if="novelPage.value.page.items.length === 0"
      description="空列表"
    />
  </div>
  <n-result
    v-if="novelPage && !novelPage.ok"
    status="error"
    title="加载错误"
    :description="novelPage.error.message"
  />
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
