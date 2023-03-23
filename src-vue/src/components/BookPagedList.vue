<script lang="ts">
interface ListOptionDescriptior {
  title: string;
  values: string[];
}
export interface ListDescriptior {
  title: string;
  options: ListOptionDescriptior[];
}
</script>

<script lang="ts" setup>
import { ref, watch } from 'vue';
import { Result, ResultState } from '../data/api/result';
import { BookListPageDto, BookRankPageDto } from '../data/api/api_novel';

type Loader = (
  page: number,
  selected: number[]
) => Promise<Result<BookListPageDto | BookRankPageDto, undefined>>;

const props = defineProps<{
  descriptior: ListDescriptior;
  loader: Loader;
}>();

const descriptior = props.descriptior;
const selected = ref(Array(descriptior.options.length).fill(0));

const currentPage = ref(1);
const pageNumber = ref(1);
const bookPage = ref<ResultState<BookListPageDto | BookRankPageDto>>();

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await props.loader(page, selected.value);
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

watch(currentPage, (page) => loadPage(page), { immediate: true });
watch(selected, (_) => refresh(), { deep: true });
</script>

<template>
  <n-h1 v-if="descriptior.title">{{ descriptior.title }}</n-h1>
  <table v-if="descriptior.options.length >= 0" style="border-spacing: 0px 8px">
    <BookListOption
      v-for="(option, index) in descriptior.options"
      :title="option.title"
      :values="option.values"
      v-model:selected="selected[index]"
    />
  </table>
  <BookList
    v-model:page="currentPage"
    :page-number="pageNumber"
    :book-page="bookPage"
  />
</template>
