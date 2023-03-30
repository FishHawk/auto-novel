<script lang="ts" setup>
import { useRoute } from 'vue-router';
import ListLayout from '../components/ListLayout.vue';
import ApiWebNovel from '../data/api/api_web_novel';

const route = useRoute();
const path = route.path;

const listDescriptors: {
  [key: string]: {
    title: string;
    search: boolean;
    options: { label: string; tags: string[] }[];
  };
} = {
  '/novel-rank/syosetu/1': {
    title: '成为小说家：流派',
    search: false,
    options: [
      {
        label: '流派',
        tags: [
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
        label: '范围',
        tags: ['每日', '每周', '每月', '季度', '每年'],
      },
    ],
  },
  '/novel-rank/syosetu/2': {
    title: '成为小说家：综合',
    search: false,
    options: [
      {
        label: '状态',
        tags: ['全部', '短篇', '连载', '完结'],
      },
      {
        label: '范围',
        tags: ['每日', '每周', '每月', '季度', '每年', '总计'],
      },
    ],
  },
  '/novel-rank/syosetu/3': {
    title: '成为小说家：异世界转移/转生',
    search: false,
    options: [
      {
        label: '状态',
        tags: ['恋爱', '幻想', '文学/科幻/其他'],
      },
      {
        label: '范围',
        tags: ['每日', '每周', '每月', '季度', '每年'],
      },
    ],
  },
};

const descriptior = listDescriptors[path];

async function loader(_page: number, _query: string, selected: number[]) {
  function optionNth(n: number): string {
    return descriptior.options[n].tags[selected[n]];
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
  return ApiWebNovel.listRank('syosetu', { type, genre, range });
}
</script>

<template>
  <ListLayout>
    <n-h1>{{ descriptior.title }}</n-h1>
    <WebBookList
      :search="descriptior.search"
      :options="descriptior.options"
      :loader="loader"
    />
  </ListLayout>
</template>
