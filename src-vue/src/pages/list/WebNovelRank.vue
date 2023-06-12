<script lang="ts" setup>
import { useRoute } from 'vue-router';

import { mapOk } from '@/data/api/result';
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { Loader } from './components/NovelList.vue';

const route = useRoute();
const providerId = route.params.providerId as string;
const typeId = route.params.typeId as string;

type Descriptor = {
  [key: string]: {
    title: string;
    search: boolean;
    options: { label: string; tags: string[] }[];
  };
};

const descriptorsKakuyomu: Descriptor = {
  '1': {
    title: 'Kakuyomu：流派',
    search: false,
    options: [
      {
        label: '流派',
        tags: [
          '综合',
          '异世界幻想',
          '现代幻想',
          '科幻',
          '恋爱',
          '浪漫喜剧',
          '现代戏剧',
          '恐怖',
          '推理',
          '散文·纪实',
          '历史·时代·传奇',
          '创作论·评论',
          '诗·童话·其他',
        ],
      },
      {
        label: '范围',
        tags: ['总计', '每年', '每月', '每周', '每日'],
      },
    ],
  },
};

const descriptorsSyosetu: Descriptor = {
  '1': {
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
        tags: ['每年', '季度', '每月', '每周', '每日'],
      },
    ],
  },
  '2': {
    title: '成为小说家：综合',
    search: false,
    options: [
      {
        label: '状态',
        tags: ['全部', '短篇', '连载', '完结'],
      },
      {
        label: '范围',
        tags: ['总计', '每年', '季度', '每月', '每周', '每日'],
      },
    ],
  },
  '3': {
    title: '成为小说家：异世界转移/转生',
    search: false,
    options: [
      {
        label: '状态',
        tags: ['恋爱', '幻想', '文学/科幻/其他'],
      },
      {
        label: '范围',
        tags: ['每年', '季度', '每月', '每周', '每日'],
      },
    ],
  },
};

const descriptiors: { [key: string]: Descriptor } = {
  syosetu: descriptorsSyosetu,
  kakuyomu: descriptorsKakuyomu,
};

const descriptior = descriptiors[providerId][typeId];

const loader: Loader = (_page: number, _query: string, selected: number[]) => {
  function optionNth(n: number): string {
    return descriptior.options[n].tags[selected[n]];
  }

  let options = {};
  if (providerId == 'syosetu') {
    const types: { [key: string]: string } = {
      '1': '流派',
      '2': '综合',
      '3': '异世界转生/转移',
    };
    const type = types[typeId];
    const genre = optionNth(0);
    const range = optionNth(1);
    options = { type, genre, range };
  } else if (providerId == 'kakuyomu') {
    const genre = optionNth(0);
    const range = optionNth(1);
    options = { genre, range };
  }
  console.log(options);
  return ApiWebNovel.listRank(providerId, options).then((result) =>
    mapOk(result, (page) => ({ type: 'web', page }))
  );
};
</script>

<template>
  <ListLayout>
    <n-h1>{{ descriptior.title }}</n-h1>
    <NovelList
      :search="descriptior.search"
      :options="descriptior.options"
      :loader="loader"
    />
  </ListLayout>
</template>
