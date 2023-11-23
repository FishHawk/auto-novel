<script lang="ts" setup>
import { useRoute } from 'vue-router';

import { menuOption } from '@/components/MainLayout.vue';
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';
import { mapOk } from '@/data/result';

import { Loader } from '../list/components/NovelList.vue';
import { useIsDesktop } from '@/data/util';
import { ref } from 'vue';

const isDesktop = useIsDesktop(600);

const route = useRoute();

const options = [
  {
    label: '排序',
    tags: ['更新时间', '收藏时间'],
  },
];

const loader: Loader<
  | (Page<WebNovelOutlineDto> & { type: 'web' })
  | (Page<WenkuNovelOutlineDto> & { type: 'wenku' })
> = (page, _query, selected) => {
  function optionNth(n: number): string {
    return options[n].tags[selected[n]];
  }
  function optionSort() {
    const option = optionNth(0);
    if (option === '更新时间') {
      return 'update';
    } else {
      return 'create';
    }
  }

  const favoriteId = (route.query.fid ?? 'default') as string;
  const favoriteType = (route.query.type ?? 'web') as string;
  if (favoriteType === 'web') {
    return ApiWebNovel.listFavorite({
      page,
      pageSize: 30,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'web', ...page })));
  } else {
    return ApiWenkuNovel.listFavorite({
      page,
      pageSize: 24,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'wenku', ...page })));
  }
};

const menuOptions = [
  {
    type: 'group',
    label: '网络小说',
    children: [menuOption('默认收藏夹', '/favorite')],
  },
  {
    key: 'divider-1',
    type: 'divider',
    props: {
      style: {
        marginLeft: '32px',
      },
    },
  },
  {
    type: 'group',
    label: '文库小说',
    children: [menuOption('默认收藏夹', '/favorite?type=wenku')],
  },
];

const showFavoriteMenuModal = ref(false);
</script>

<template>
  <AccountLayout>
    <div style="display: flex">
      <div style="flex: auto">
        <n-button
          v-if="!isDesktop"
          @click="showFavoriteMenuModal = true"
          style="margin-bottom: 8px"
        >
          收藏夹列表
        </n-button>
        <NovelList
          :search="false"
          :options="options"
          :loader="loader"
          v-slot="{ page }"
        >
          <NovelListWeb v-if="page.type === 'web'" :items="page.items" simple />
          <NovelListWenku v-if="page.type === 'wenku'" :items="page.items" />
        </NovelList>
      </div>
      <n-menu
        v-if="isDesktop"
        :value="route.fullPath"
        :options="menuOptions"
        style="flex: none; width: 250px"
      />
    </div>

    <n-modal v-model:show="showFavoriteMenuModal">
      <n-card
        style="width: min(600px, calc(100% - 16px))"
        :bordered="false"
        size="large"
        role="dialog"
        aria-modal="true"
      >
        <n-scrollbar trigger="none" style="max-height: 400px">
          <n-menu :value="route.fullPath" :options="menuOptions" />
        </n-scrollbar>
      </n-card>
    </n-modal>
  </AccountLayout>
</template>
