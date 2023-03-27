<script lang="ts" setup>
import ApiNovel from '../data/api/api_novel';

const descriptior = {
  title: '已缓存小说',
  options: [
    {
      title: '来源',
      values: [
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
      title: '排序',
      values: ['更新时间', '创建时间'],
    },
  ],
};

async function loader(page: number, selected: number[]) {
  function optionNth(n: number): string {
    return descriptior.options[n].values[selected[n]];
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
  const sortMap: { [key: string]: string } = {
    更新时间: 'changed',
    创建时间: 'created',
  };
  return ApiNovel.list(
    page - 1,
    providerMap[optionNth(0)],
    sortMap[optionNth(1)]
  );
}
</script>

<template>
  <ListLayout>
    <BookPagedList :descriptior="descriptior" :loader="loader" />
  </ListLayout>
</template>
