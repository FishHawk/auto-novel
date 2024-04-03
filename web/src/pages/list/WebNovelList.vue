<script lang="ts" setup>
import { FormatListBulletedOutlined } from '@vicons/material';

import { WebNovelRepository } from '@/data/api';
import { useUserDataStore } from '@/data/stores/user_data';
import { Page } from '@/model/Page';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { Locator } from '@/data';
import { useIsWideScreen } from '@/pages/util';
import { runCatching } from '@/util/result';

import { Loader } from './components/NovelList.vue';
import { menuOptions } from './components/menu';

const isWideScreen = useIsWideScreen(850);
const route = useRoute();
const userData = useUserDataStore();

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
      'Kakuyomu',
      '成为小说家吧',
      'Novelup',
      'Hameln',
      'Pixiv',
      'Alphapolis',
    ],
    multiple: true,
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
  if (query !== '') {
    document.title = '网络小说 搜索：' + query;
  }
  const parseProviderBitFlags = (n: number): string => {
    const providerMap: { [key: string]: string } = {
      Kakuyomu: 'kakuyomu',
      成为小说家吧: 'syosetu',
      Novelup: 'novelup',
      Hameln: 'hameln',
      Pixiv: 'pixiv',
      Alphapolis: 'alphapolis',
    };
    return options[n].tags
      .filter((_, index) => (selected[n] & (1 << index)) !== 0)
      .map((tag) => providerMap[tag])
      .join();
  };

  return runCatching(
    WebNovelRepository.listNovel({
      page,
      pageSize: 20,
      query,
      provider: parseProviderBitFlags(0),
      type: selected[1],
      ...(userData.isOldAss
        ? {
            level: selected[2],
            translate: selected[3],
            sort: selected[4],
          }
        : {
            level: 1,
            translate: selected[2],
            sort: selected[3],
          }),
    })
  );
};

const webSearchHistoryRepository = Locator.webSearchHistoryRepository();

const search = computed(() => {
  const searchHistory = webSearchHistoryRepository.ref.value;
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
    webSearchHistoryRepository.addHistory(query);
  },
  { immediate: true }
);

const showListModal = ref(false);
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="250" class="layout-content">
    <n-h1>网络小说</n-h1>

    <div style="margin-bottom: 24px">
      <c-button
        v-if="!isWideScreen"
        label="列表/排行"
        :icon="FormatListBulletedOutlined"
        @action="showListModal = true"
      />
    </div>

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

    <c-drawer-right
      v-if="!isWideScreen"
      v-model:show="showListModal"
      title="列表/排行"
    >
      <n-menu :value="route.path" :options="menuOptions" />
    </c-drawer-right>
  </c-layout>
</template>
