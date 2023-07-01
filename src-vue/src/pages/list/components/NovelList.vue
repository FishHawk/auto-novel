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

const props = withDefaults(
  defineProps<{
    search: boolean;
    options: { label: string; tags: string[] }[];
    loader: Loader;
  }>(),
  {
    search: false,
    options: () => [],
  }
);

const currentPage = ref(parseInt(route.query.page as string) || 1);
const pageNumber = ref(1);
const novelPageResult = ref<ResultState<NovelPage>>();

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
  novelPageResult.value = undefined;

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
    novelPageResult.value = result;
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
  <ResultView :result="novelPageResult" v-slot="{ value: page }">
    <NovelListWeb v-if="page.type === 'web'" :items="page.page.items" />
    <NovelListWenku v-else :items="page.page.items" />
  </ResultView>
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
