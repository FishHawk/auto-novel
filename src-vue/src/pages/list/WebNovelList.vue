<script lang="ts" setup>
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { mapOk } from '@/data/api/result';

import { Loader } from './components/NovelList.vue';

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
      'Novelism',
    ],
  },
];

const loader: Loader = (page: number, query: string, selected: number[]) => {
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
    Novelism: 'novelism',
  };

  return ApiWebNovel.list(page - 1, 10, providerMap[optionNth(0)], query).then(
    (result) => mapOk(result, (page) => ({ type: 'web', page }))
  );
};
</script>

<template>
  <ListLayout>
    <n-h1>网络小说</n-h1>
    <NovelList search :options="options" :loader="loader" />
  </ListLayout>
</template>
