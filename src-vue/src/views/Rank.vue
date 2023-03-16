<script lang="ts" setup>
import { useRoute } from 'vue-router';
import { ListDescriptior } from '../components/BookPagedList.vue';
import ListLayout from '../components/ListLayout.vue';
import ApiNovel from '../data/api/api_novel';

const route = useRoute();
const path = route.path;

const listDescriptors: { [key: string]: ListDescriptior } = {
  '/rank/syosetu/1': {
    title: '成为小说家：流派',
    options: [
      {
        title: '流派',
        values: [
          '恋爱：异世界',
          '恋爱：现实世界',
          '幻想：高幻想',
          '幻想：低幻想',
          '文学：纯文学',
          '文学：人性剧',
          '文学：历史',
          '文学：推理',
          '文学：恐怖',
          '文学：动作',
          '文学：喜剧',
          '科幻：VR游戏',
          '科幻：宇宙',
          '科幻：空想科学',
          '科幻：惊悚',
          '其他：童话',
          '其他：诗',
          '其他：散文',
          '其他：其他',
        ],
      },
      {
        title: '范围',
        values: ['每日', '每周', '每月', '季度', '每年'],
      },
    ],
  },
  '/rank/syosetu/2': {
    title: '成为小说家：综合',
    options: [
      {
        title: '状态',
        values: ['全部', '短篇', '连载', '完结'],
      },
      {
        title: '范围',
        values: ['每日', '每周', '每月', '季度', '每年', '总计'],
      },
    ],
  },
  '/rank/syosetu/3': {
    title: '成为小说家：异世界转移/转生',
    options: [
      {
        title: '状态',
        values: ['恋爱', '幻想', '文学/科幻/其他'],
      },
      {
        title: '范围',
        values: ['每日', '每周', '每月', '季度', '每年'],
      },
    ],
  },
};

const descriptior = listDescriptors[path];

async function loader(page: number, selected: number[]) {
  function optionNth(n: number): string {
    return descriptior.options[n].values[selected[n]];
  }
  let type: string;
  if (path == '/rank/syosetu/1') {
    type = '流派';
  } else if (path == '/rank/syosetu/2') {
    type = '综合';
  } else if (path == '/rank/syosetu/3') {
    type = '异世界转生/转移';
  } else {
    type = '流派'; // default
  }
  const genre = optionNth(0);
  const range = optionNth(1);
  return ApiNovel.listRank('syosetu', { type, genre, range });
}
</script>

<template>
  <ListLayout>
    <div>
      <BookPagedList :descriptior="descriptior" :loader="loader" />
    </div>
  </ListLayout>
</template>
