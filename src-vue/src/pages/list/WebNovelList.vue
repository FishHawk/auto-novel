<script lang="ts" setup>
import ApiWebNovel from '@/data/api/api_web_novel';

const descriptior = {
  search: true,
  options: [
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
  ],
};

async function loader(page: number, query: string, selected: number[]) {
  function optionNth(n: number): string {
    return descriptior.options[n].tags[selected[n]];
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

  return ApiWebNovel.list(page - 1, 10, providerMap[optionNth(0)], query);
}
</script>

<template>
  <ListLayout>
    <n-h1>网络小说</n-h1>
    <WebBookList
      :search="descriptior.search"
      :options="descriptior.options"
      :loader="loader"
    />
  </ListLayout>
</template>
