<script lang="ts" setup>
import { useRoute } from 'vue-router';

import { menuOption } from '@/components/MainLayout.vue';
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';
import { mapOk } from '@/data/result';

import { Loader } from './components/NovelList.vue';
import { useIsDesktop } from '@/data/util';

const route = useRoute();
const favoriteId = route.params.favoriteId as string;

const options = [
  {
    label: '排序',
    tags: ['更新时间', '收藏时间'],
  },
];

const loader: Loader<
  | (Page<WebNovelOutlineDto> & { type: 'web' })
  | (Page<WenkuNovelOutlineDto> & { type: 'wenku' })
> = (page, _query, selected) => {
  function optionNth(n: number): string {
    return options[n].tags[selected[n]];
  }
  function optionSort() {
    const option = optionNth(0);
    if (option === '更新时间') {
      return 'update';
    } else {
      return 'create';
    }
  }
  if (favoriteId === 'web') {
    return ApiWebNovel.listFavorite({
      page,
      pageSize: 30,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'web', ...page })));
  } else {
    return ApiWenkuNovel.listFavorite({
      page,
      pageSize: 24,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'wenku', ...page })));
  }
};

const menuOptions = [
  menuOption('网络小说', '/favorite/web'),
  menuOption('文库小说', '/favorite/wenku'),
];

const isDesktop = useIsDesktop(850);
</script>

<template>
  <MainLayout :side-menu-options="menuOptions">
    <n-h1>我的收藏</n-h1>
    <n-space v-if="!isDesktop">
      <n-text>收藏夹</n-text>
      <RouterNA to="/favorite/web">网络小说</RouterNA>
      <RouterNA to="/favorite/wenku">文库小说</RouterNA>
    </n-space>
    <NovelList
      :search="false"
      :options="options"
      :loader="loader"
      v-slot="{ page }"
    >
      <NovelListWeb v-if="page.type === 'web'" :items="page.items" simple />
      <NovelListWenku v-if="page.type === 'wenku'" :items="page.items" />
    </NovelList>
  </MainLayout>
</template>
