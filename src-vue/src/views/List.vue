<script lang="ts" setup>
import { h, onMounted, ref, watch } from 'vue';
import { useRoute, RouterLink } from 'vue-router';
import { MenuOption } from 'naive-ui';
import { MenuFilled } from '@vicons/material';

import { Result, ResultState } from '../api/result';
import ApiNovel, { BookPageDto } from '../api/api_novel';

let route = useRoute();

const options = ref<{ title: string; values: string[] }[]>([]);
const selected = ref<number[]>([]);

onMounted(() => {
  const path = route.path;
  if (path == '/list') {
    options.value = [
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
    ];
  } else if (path == '/rank/syosetu/1') {
    options.value = [
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
    ];
  } else if (path == '/rank/syosetu/2') {
    options.value = [
      {
        title: '状态',
        values: ['全部', '短篇', '连载', '完结'],
      },
      {
        title: '范围',
        values: ['每日', '每周', '每月', '季度', '每年', '总计'],
      },
    ];
  } else if (path == '/rank/syosetu/3') {
    options.value = [
      {
        title: '状态',
        values: ['恋爱', '幻想', '文学/科幻/其他'],
      },
      {
        title: '范围',
        values: ['每日', '每周', '每月', '季度', '每年'],
      },
    ];
  }

  selected.value = Array(options.value.length).fill(0);
});

function optionNth(n: number): string {
  return options.value[n].values[selected.value[n]];
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

watch(currentPage, (page) => loadPage(page), { immediate: true });
watch(
  selected,
  (_) => {
    currentPage.value = 1;
    loadPage(currentPage.value);
  },
  { deep: true }
);

const topMenuOptions: MenuOption[] = [
  {
    label: () =>
      h(RouterLink, { to: { path: '/' } }, { default: () => '首页' }),
    key: '/1',
  },
  {
    label: () =>
      h(RouterLink, { to: { path: '/list' } }, { default: () => '列表' }),
    key: '/list',
  },
];

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
  <n-layout>
    <n-layout-header bordered>
      <n-popover trigger="click">
        <template #trigger>
          <n-icon size="24" class="on-mobile" style="padding-bottom: 0px">
            <MenuFilled />
          </n-icon>
        </template>
        <n-menu v-model:value="$route.path" :options="menuOptions" />
      </n-popover>
      <n-menu
        :value="$route.path == '/' ? '/' : '/list'"
        mode="horizontal"
        :options="topMenuOptions"
      />
    </n-layout-header>
    <n-layout has-sider>
      <n-layout-sider bordered class="on-desktop">
        <n-menu
          v-model:value="$route.path"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :options="menuOptions"
        />
      </n-layout-sider>
      <n-layout-content content-style="padding: 24px;">
        <div class="content">
          <table style="border-spacing: 12px">
            <BookListOption
              v-for="(option, index) in options"
              :title="option.title"
              :values="option.values"
              v-model:selected="selected[index]"
            />
          </table>
          <BookList
            :currentPage="currentPage"
            :pageNumber="pageNumber"
            :bookPage="bookPage"
          />
        </div>
      </n-layout-content>
    </n-layout>
  </n-layout>
</template>
