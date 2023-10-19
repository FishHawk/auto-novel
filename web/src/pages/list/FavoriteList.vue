<script lang="ts" setup>
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';
import { mapOk } from '@/data/result';

import { Loader } from './components/NovelList.vue';

const options = [
  {
    label: '类型',
    tags: ['网页小说', '文库小说'],
  },
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
    const option = optionNth(1);
    if (option === '更新时间') {
      return 'update';
    } else {
      return 'create';
    }
  }
  if (optionNth(0) === '网页小说') {
    return ApiWebNovel.listFavored({
      page,
      pageSize: 30,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'web', ...page })));
  } else {
    return ApiWenkuNovel.listFavored({
      page,
      pageSize: 24,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'wenku', ...page })));
  }
};
</script>

<template>
  <ListLayout>
    <n-h1>我的收藏</n-h1>
    <NovelList
      :search="false"
      :options="options"
      :loader="loader"
      v-slot="{ page }"
    >
      <NovelListWeb v-if="page.type === 'web'" :items="page.items" simple />
      <NovelListWenku v-if="page.type === 'wenku'" :items="page.items" />
    </NovelList>
  </ListLayout>
</template>
