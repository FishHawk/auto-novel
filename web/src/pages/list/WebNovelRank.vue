<script lang="ts" setup>
import { Locator } from '@/data';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { runCatching } from '@/util/result';

import { useIsWideScreen } from '@/pages/util';
import { Loader } from './components/NovelPage.vue';

const props = defineProps<{
  providerId: string;
  typeId: string;
  page: number;
  selected: number[];
}>();

const isWideScreen = useIsWideScreen();
const route = useRoute();

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

const commonOptionsSyosetu = [
  {
    label: '范围',
    tags: ['总计', '每年', '季度', '每月', '每周', '每日'],
  },
  {
    label: '状态',
    tags: ['全部', '短篇', '连载', '完结'],
  },
];
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
      ...commonOptionsSyosetu,
    ],
  },
  '2': {
    title: '成为小说家：综合',
    search: false,
    options: commonOptionsSyosetu,
  },
  '3': {
    title: '成为小说家：异世界转移/转生',
    search: false,
    options: [
      {
        label: '流派',
        tags: ['恋爱', '幻想', '文学/科幻/其他'],
      },
      ...commonOptionsSyosetu,
    ],
  },
};

const descriptiors: { [key: string]: Descriptor } = {
  syosetu: descriptorsSyosetu,
  kakuyomu: descriptorsKakuyomu,
};

const descriptior = computed(
  () => descriptiors[props.providerId][props.typeId],
);

const loader = computed<Loader<WebNovelOutlineDto>>(() => {
  const providerId = props.providerId;
  const typeId = props.typeId;

  return (page, _query, selected) => {
    const optionNth = (n: number): string =>
      descriptior.value.options[n].tags[selected[n]];

    let filters = {};
    if (providerId == 'syosetu') {
      const types: { [key: string]: string } = {
        '1': '流派',
        '2': '综合',
        '3': '异世界转生/转移',
      };
      if (typeId === '2') {
        filters = {
          type: types[typeId],
          range: optionNth(0),
          status: optionNth(1),
          page,
        };
      } else {
        filters = {
          type: types[typeId],
          genre: optionNth(0),
          range: optionNth(1),
          status: optionNth(2),
          page,
        };
      }
    } else if (providerId == 'kakuyomu') {
      filters = { genre: optionNth(0), range: optionNth(1) };
    }
    return runCatching(
      Locator.webNovelRepository.listRank(providerId, filters),
    );
  };
});
</script>

<template>
  <div class="layout-content">
    <n-h1>{{ descriptior.title }}</n-h1>

    <novel-page
      :page="page"
      :selected="selected"
      :loader="loader"
      :search="
        descriptior.search
          ? {
              suggestions: [],
              tags: [],
            }
          : undefined
      "
      :options="descriptior.options"
      loadingType="webNovel"
      v-slot="{ items }"
    >
      <novel-list-web :items="items" />
    </novel-page>
  </div>
</template>
