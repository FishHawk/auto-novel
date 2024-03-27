<script lang="ts" setup generic="T extends Page<any>">
import { LocationQuery } from 'vue-router';
import { onKeyStroke } from '@vueuse/core';

import { Page } from '@/model/Page';
import { Result } from '@/util/result';

export type Loader<T extends Page<any>> = (
  page: number,
  query: string,
  selected: number[]
) => Promise<Result<T>>;

const route = useRoute();
const router = useRouter();

const props = defineProps<{
  search?: { suggestions: string[]; tags: string[] };
  options: { label: string; tags: string[]; multiple?: boolean }[];
  loader: Loader<T>;
}>();

const parsePage = (q: typeof route.query) => {
  return parseInt(q.page as string) || 1;
};
const parseFilters = (q: typeof route.query) => {
  let query = '';
  if (typeof q.query === 'string') {
    query = q.query;
  }
  let selected: number[] = props.options.map((it) =>
    it.multiple === true ? 2 ** it.tags.length - 1 : 0
  );
  if (typeof q.selected === 'string') {
    selected[0] = parseInt(q.selected) || 0;
  } else if (q.selected) {
    q.selected.forEach((it, index) => {
      selected[index] = parseInt(it ?? '') || 0;
    });
  }
  return { query, selected };
};

const filters = ref(parseFilters(route.query));
const currentPage = ref(parsePage(route.query));
const pageNumber = ref(1);
const novelPageResult = ref<Result<T>>();

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
      currentPage.value - 1,
      filters.value.query,
      filters.value.selected
    );
    if (currentPage.value === newPage) {
      novelPageResult.value = result;
      if (result.ok) {
        pageNumber.value = result.value.pageNumber;
      }
    }
  },
  { immediate: true }
);

function pushPath() {
  const query: LocationQuery = {};
  for (const key in route.query) {
    query[key] = route.query[key];
  }

  if ('page' in query) delete query['page'];
  query['page'] = currentPage.value.toString();

  if ('query' in query) delete query['query'];
  if (props.search !== undefined) {
    query.query = filters.value.query;
  }

  if ('selected' in query) delete query['selected'];
  if (props.options.length > 0) {
    query.selected = filters.value.selected.map((it) => it.toString());
  }

  router.push({ path: route.path, query });
}

watch(currentPage, (_) => pushPath());
function detectUserInput() {
  currentPage.value = 1;
  pushPath();
}

onKeyStroke(['ArrowLeft'], (e) => {
  if (currentPage.value > 1) {
    currentPage.value = currentPage.value - 1;
    e.preventDefault();
  }
});
onKeyStroke(['ArrowRight'], (e) => {
  if (currentPage.value < pageNumber.value) {
    currentPage.value = currentPage.value + 1;
    e.preventDefault();
  }
});
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

  <c-result
    :result="novelPageResult"
    :show-empty="(it: T) => it.items.length === 0"
    v-slot="{ value: page }"
  >
    <slot :page="page" />
  </c-result>

  <n-divider />
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
