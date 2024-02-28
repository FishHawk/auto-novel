<script lang="ts" setup>
import { useRoute } from 'vue-router';

import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { Page } from '@/data/api/common';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsWideScreen } from '@/data/util';

import { Loader } from './components/NovelList.vue';
import { menuOptions } from './components/menu';
import { computed, watch } from 'vue';
import { useWebSearchHistoryStore } from '@/data/stores/search_history';

const isWideScreen = useIsWideScreen(850);
const route = useRoute();
const userData = useUserDataStore();
const searchHistory = useWebSearchHistoryStore();

const oldAssOptions = userData.isOldAss
  ? [
      {
        label: '分级',
        tags: ['全部', '一般向', 'R18'],
      },
    ]
  : [];
const options = [
  {
    label: '来源',
    tags: [
      '全部',
      'Kakuyomu',
      '成为小说家吧',
      'Novelup',
      'Hameln',
      'Pixiv',
      'Alphapolis',
    ],
  },
  {
    label: '类型',
    tags: ['全部', '连载中', '已完结', '短篇'],
  },
  ...oldAssOptions,
  {
    label: '翻译',
    tags: ['全部', 'GPT', 'Sakura'],
  },
  {
    label: '排序',
    tags: ['更新', '点击', '相关'],
  },
];

const loader: Loader<Page<WebNovelOutlineDto>> = (page, query, selected) => {
  function optionNth(n: number): string {
    return options[n].tags[selected[n]];
  }
  const providerMap: { [key: string]: string } = {
    全部: '',
    Kakuyomu: 'kakuyomu',
    成为小说家吧: 'syosetu',
    Novelup: 'novelup',
    Hameln: 'hameln',
    Pixiv: 'pixiv',
    Alphapolis: 'alphapolis',
  };
  if (userData.isOldAss) {
    return ApiWebNovel.listNovel({
      page,
      pageSize: 20,
      query,
      provider: providerMap[optionNth(0)],
      type: selected[1],
      level: selected[2],
      translate: selected[3],
      sort: selected[4],
    });
  } else {
    return ApiWebNovel.listNovel({
      page,
      pageSize: 20,
      query,
      provider: providerMap[optionNth(0)],
      type: selected[1],
      level: 1,
      translate: selected[2],
      sort: selected[3],
    });
  }
};

const search = computed(() => {
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
    searchHistory.addHistory(query);
  },
  { immediate: true }
);
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="250" class="layout-content">
    <n-h1>网络小说</n-h1>
    <NovelList
      :search="search"
      :options="options"
      :loader="loader"
      v-slot="{ page }"
    >
      <NovelListWeb :items="page.items" />
    </NovelList>

    <template #sidebar>
      <n-menu :value="route.path" :options="menuOptions" />
    </template>
  </c-layout>
</template>
