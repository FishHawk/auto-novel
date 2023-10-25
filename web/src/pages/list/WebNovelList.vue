<script lang="ts" setup>
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { Page } from '@/data/api/common';

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
  {
    label: '类型',
    tags: ['全部', '连载中', '已完结', '短篇'],
  },
  {
    label: '分级',
    tags: ['全部', '一般向', 'R18'],
  },
  {
    label: '翻译',
    tags: ['全部', 'AI'],
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
    Novelism: 'novelism',
  };
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
};
</script>

<template>
  <ListLayout>
    <n-h1>网络小说</n-h1>
    <n-text depth="3" style="font-size: 12px">
      # 搜索语法参见
      <RouterNA to="/forum/64f3da23794cbb1321145c08">如何使用搜索</RouterNA>
    </n-text>
    <NovelList search :options="options" :loader="loader" v-slot="{ page }">
      <NovelListWeb :items="page.items" />
    </NovelList>
  </ListLayout>
</template>
