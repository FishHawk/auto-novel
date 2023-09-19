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

const filters = ref(parseFilters(route.query));
const currentPage = ref(parsePage(route.query));
const pageNumber = ref(1);
const novelPageResult = ref<ResultState<NovelPage>>();

function parsePage(q: typeof route.query) {
  return parseInt(route.query.page as string) || 1;
}
function parseFilters(q: typeof route.query) {
  let query = '';
  if (typeof q.query === 'string') {
    query = q.query;
  }
  let selected: number[] = Array(props.options.length).fill(0);
  if (typeof q.selected === 'string') {
    selected[0] = parseInt(q.selected) || 0;
  } else if (q.selected) {
    q.selected.forEach((it, index) => {
      selected[index] = parseInt(it ?? '') || 0;
    });
  }
  return { query, selected };
}

watch(
  route,
  async (route) => {
    const compare = (a1: number[], a2: number[]) =>
      a1.length == a2.length &&
      a1.every((element, index) => element === a2[index]);

    const newFilters = parseFilters(route.query);
    if (newFilters.query !== filters.value.query) {
      filters.value.query = newFilters.query;
    }
    if (!compare(newFilters.selected, filters.value.selected)) {
      filters.value.selected = newFilters.selected;
    }
    const newPage = parsePage(route.query);
    if (newPage !== currentPage.value) {
      currentPage.value = newPage;
    }

    novelPageResult.value = undefined;
    const result = await props.loader(
      currentPage.value,
      filters.value.query,
      filters.value.selected
    );
    if (currentPage.value === newPage) {
      novelPageResult.value = result;
      if (result.ok) {
        pageNumber.value = result.value.page.pageNumber;
      }
    }
  },
  { immediate: true }
);

function pushPath() {
  const query: { [key: string]: any } = { page: currentPage.value };
  if (props.search) {
    query.query = filters.value.query;
  }
  if (props.options.length > 0) {
    query.selected = filters.value.selected;
  }
  router.push({ path: route.path, query });
}

watch(currentPage, (_) => pushPath());
function detectUserInput() {
  currentPage.value = 1;
  pushPath();
}
</script>

<template>
  <ListFilters
    :search="search"
    :options="options"
    :filters="filters"
    @user-input="detectUserInput"
  />

  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
    style="margin-top: 20px"
  />
  <n-divider />
  <ResultView
    :result="novelPageResult"
    :showEmpty="(it: NovelPage) => it.page.items.length === 0"
    v-slot="{ value: page }"
  >
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
