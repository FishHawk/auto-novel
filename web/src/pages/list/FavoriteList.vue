<script lang="ts" setup>
import { mapOk } from '@/data/api/result';

import { Loader } from './components/NovelList.vue';
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';

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

const loader: Loader = (page, _query, selected) => {
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
    return ApiWebNovel.listFavored(page - 1, 10, optionSort()).then((result) =>
      mapOk(result, (page) => ({ type: 'web', page }))
    );
  } else {
    return ApiWenkuNovel.listFavored(page - 1, 24, optionSort()).then(
      (result) => mapOk(result, (page) => ({ type: 'wenku', page }))
    );
  }
};
</script>

<template>
  <ListLayout>
    <n-h1>我的收藏</n-h1>
    <NovelList :options="options" :loader="loader" />
  </ListLayout>
</template>