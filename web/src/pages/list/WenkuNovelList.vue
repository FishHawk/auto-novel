<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WenkuNovelRepository } from '@/data/api';
import { WenkuNovelOutlineDto } from '@/model/WenkuNovel';
import { runCatching } from '@/util/result';

import { Loader } from './components/NovelPage.vue';

defineProps<{
  page: number;
  query: string;
  selected: number[];
}>();

const route = useRoute();

const { createAtLeastOneMonth } = Locator.authRepository();

const options = [
  {
    label: '分级',
    tags: createAtLeastOneMonth.value
      ? ['一般向', '成人向', '严肃向']
      : ['一般向', '严肃向'],
  },
];

const loader: Loader<WenkuNovelOutlineDto> = (page, query, selected) => {
  if (query !== '') {
    document.title = '文库小说 搜索：' + query;
  }
  let level = selected[0] + 1;
  if (!createAtLeastOneMonth.value && level === 2) {
    level = 3;
  }
  return runCatching(
    WenkuNovelRepository.listNovel({
      page,
      pageSize: 24,
      query,
      level,
    }),
  );
};

const wenkuSearchHistoryRepository = Locator.wenkuSearchHistoryRepository();

const search = computed(() => {
  const searchHistory = wenkuSearchHistoryRepository.ref.value;
  return {
    suggestions: searchHistory.queries,
    tags: searchHistory.tags
      .sort((a, b) => Math.log2(b.used) - Math.log2(a.used))
      .map((it) => it.tag)
      .slice(0, 8),
  };
});

watch(
  route,
  async (route) => {
    let query = '';
    if (typeof route.query.query === 'string') {
      query = route.query.query;
    }
    wenkuSearchHistoryRepository.addHistory(query);
  },
  { immediate: true },
);
</script>

<template>
  <div class="layout-content">
    <n-h1>文库小说</n-h1>

    <router-link to="/wenku-edit">
      <c-button
        label="新建小说"
        :icon="PlusOutlined"
        style="margin-bottom: 8px"
      />
    </router-link>

    <novel-page
      :page="page"
      :query="query"
      :selected="selected"
      :loader="loader"
      :search="search"
      :options="options"
      v-slot="{ items }"
    >
      <novel-list-wenku :items="items" />
    </novel-page>
  </div>
</template>

<style scoped>
.n-card-header__main {
  text-overflow: ellipsis;
}
</style>
