<script lang="ts" setup>
import { h, onMounted, ref, watch } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import { MenuOption } from 'naive-ui';

import { Result, ResultState } from '../api/result';
import ApiNovel, { BookPageDto } from '../api/api_novel';

interface ListOptionDescriptior {
  title: string;
  values: string[];
}
interface ListDescriptior {
  title: string;
  options: ListOptionDescriptior[];
}

const listDescriptors: { [key: string]: ListDescriptior } = {
  '/list': {
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
        ],
      },
      {
        title: '排序',
        values: ['更新时间', '创建时间'],
      },
    ],
  },
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

const route = useRoute();

const descriptior = ref<ListDescriptior>();
const selected = ref<number[]>([]);

onMounted(() => {
  const path = route.path;
  const selectedDescriptor = listDescriptors[path];
  descriptior.value = selectedDescriptor;
  selected.value = Array(selectedDescriptor.options.length).fill(0);
});

function optionNth(n: number): string {
  return descriptior.value!.options[n].values[selected.value[n]];
}

const currentPage = ref(1);
const pageNumber = ref(1);
const bookPage = ref<ResultState<BookPageDto>>();

async function loadPage(page: number) {
  const path = route.path;
  bookPage.value = undefined;

  let result: Result<BookPageDto>;
  if (path == '/list') {
    const providerMap: { [key: string]: string } = {
      全部: '',
      Kakuyomu: 'kakuyomu',
      成为小说家吧: 'syosetu',
      Novelup: 'novelup',
      Hameln: 'hameln',
      Pixiv: 'pixiv',
    };
    const sortMap: { [key: string]: string } = {
      更新时间: 'changed',
      创建时间: 'created',
    };

    result = await ApiNovel.list(
      currentPage.value - 1,
      providerMap[optionNth(0)],
      sortMap[optionNth(1)]
    );
  } else if (path.startsWith('/rank/syosetu/')) {
    let type: string;
    if (path == '/rank/syosetu/1') {
      type = '流派';
    } else if (path == '/rank/syosetu/2') {
      type = '综合';
    } else if (path == '/rank/syosetu/3') {
      type = '异世界转生/转移';
    } else {
      return;
    }
    const genre = optionNth(0);
    const range = optionNth(1);
    result = await ApiNovel.listRank('syosetu', { type, genre, range });
  } else {
    return;
  }

  if (currentPage.value == page) {
    bookPage.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

watch(currentPage, (page) => {
  if (descriptior.value && descriptior.value.options.length > 0) {
    loadPage(page);
  }
});
watch(
  selected,
  (_) => {
    currentPage.value = 1;
    loadPage(currentPage.value);
  },
  { deep: true }
);

const menuOptions: MenuOption[] = [
  {
    label: () =>
      h(RouterLink, { to: { path: '/list' } }, { default: () => '已缓存小说' }),
    key: '/list',
  },
  {
    label: () =>
      h(
        RouterLink,
        { to: { path: '/rank/syosetu/1' } },
        { default: () => '成为小说家：流派' }
      ),
    key: '/rank/syosetu/1',
  },
  {
    label: () =>
      h(
        RouterLink,
        { to: { path: '/rank/syosetu/2' } },
        { default: () => '成为小说家：综合' }
      ),
    key: '/rank/syosetu/2',
  },
  {
    label: () =>
      h(
        RouterLink,
        { to: { path: '/rank/syosetu/3' } },
        { default: () => '成为小说家：异世界转移/转生' }
      ),
    key: '/rank/syosetu/3',
  },
];
</script>

<template>
  <n-layout has-sider>
    <n-layout-sider bordered class="on-desktop">
      <n-menu
        v-model:value="$route.path"
        :collapsed-width="64"
        :options="menuOptions"
      />
    </n-layout-sider>
    <n-layout-content>
      <div class="content">
        <n-h1>{{ descriptior?.title }}</n-h1>
        <table style="border-spacing: 0px 8px">
          <BookListOption
            v-for="(option, index) in descriptior?.options"
            :title="option.title"
            :values="option.values"
            v-model:selected="selected[index]"
          />
        </table>
        <BookList
          v-model:page="currentPage"
          :page-number="pageNumber"
          :book-page="bookPage"
        />
      </div>
    </n-layout-content>
  </n-layout>
</template>
