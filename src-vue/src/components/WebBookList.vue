<script lang="ts" setup>
import { ref, watch } from 'vue';

import { Result, ResultState } from '../data/api/result';
import { BookListPageDto, BookRankPageDto } from '../data/api/api_web_novel';
import { buildMetadataUrl } from '../data/provider';

type Loader = (
  page: number,
  query: string,
  selected: number[]
) => Promise<Result<BookListPageDto | BookRankPageDto>>;

const props = defineProps<{
  search: boolean;
  options: { label: string; tags: string[] }[];
  loader: Loader;
}>();

const currentPage = ref(1);
const pageNumber = ref(1);
const bookPage = ref<ResultState<BookListPageDto | BookRankPageDto>>();

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
    <div v-for="item in bookPage.value.items">
      <n-h3 class="title" style="margin-bottom: 4px">
        <n-a :href="`/novel/${item.providerId}/${item.bookId}`" target="_blank">
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
      <template v-if="'extra' in item">
        <div v-for="extraLine in item.extra.split('\n')" style="color: #666">
          {{ extraLine }}
        </div>
      </template>
      <template v-else>
        <div style="color: #666">
          日文({{ item.count }}/{{ item.total }}) 百度({{ item.countBaidu }}/{{
            item.total
          }}) 有道({{ item.countYoudao }}/{{ item.total }})
        </div>
      </template>
      <n-divider />
    </div>

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
