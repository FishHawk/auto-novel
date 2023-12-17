<script lang="ts" setup>
import { useRoute, useRouter } from 'vue-router';
import { computed, h, ref } from 'vue';
import { MenuOption, NButton, useMessage } from 'naive-ui';

import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/common';
import { mapOk } from '@/data/result';
import { useIsDesktop } from '@/data/util';

import FavoriteMenuItem from './components/FavoriteMenuItem.vue';
import { Loader } from '../list/components/NovelList.vue';
import { ApiUser, FavoredList } from '@/data/api/api_user';

const isDesktop = useIsDesktop(600);

const route = useRoute();
const router = useRouter();
const message = useMessage();

const favoriteType = computed(() => (route.query.type ?? 'web') as string);
const favoriteId = computed(() => (route.query.fid ?? 'default') as string);

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

  if (favoriteType.value === 'web') {
    return ApiUser.listFavoredWebNovel(favoriteId.value, {
      page,
      pageSize: 30,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'web', ...page })));
  } else {
    return ApiUser.listFavoredWenkuNovel(favoriteId.value, {
      page,
      pageSize: 24,
      sort: optionSort(),
    }).then((result) => mapOk(result, (page) => ({ type: 'wenku', ...page })));
  }
};

const favoredList = ref<FavoredList>({
  web: [{ id: 'default', title: '默认收藏夹' }],
  wenku: [{ id: 'default', title: '默认收藏夹' }],
});

const loadFavoredList = async () => {
  const result = await ApiUser.listFavored();
  if (result.ok) {
    favoredList.value = result.value;
    const ids = (
      favoriteType.value === 'web' ? result.value.web : result.value.wenku
    ).map((it) => it.id);
    if (!ids.includes(favoriteId.value)) {
      router.push({ path: `favorite?type=${favoriteType.value}` });
    }
  } else {
    message.error('收藏夹加载失败:' + result.error.message);
  }
};
loadFavoredList();

const favoriteMenuOption = (
  type: 'web' | 'wenku',
  id: string,
  title: string
): MenuOption => ({
  label: () =>
    h(FavoriteMenuItem, {
      id,
      title,
      type,
      onUpdated: loadFavoredList,
      onDeleted: loadFavoredList,
    }),
  key: type + id,
});

const currentMenuKey = computed(() => favoriteType.value + favoriteId.value);
const menuOptions = computed(() => [
  {
    type: 'group',
    label: '操作',
    children: [
      <MenuOption>{
        label: () =>
          h(
            'a',
            {
              onClick() {
                showAddModal.value = true;
              },
            },
            '新建收藏夹'
          ),
      },
    ],
  },
  {
    type: 'divider',
    key: 'divider-1',
    props: { style: { marginLeft: '32px' } },
  },
  {
    type: 'group',
    label: '网络小说',
    children: favoredList.value.web.map(({ id, title }) =>
      favoriteMenuOption('web', id, title)
    ),
  },
  {
    type: 'divider',
    key: 'divider-2',
    props: { style: { marginLeft: '32px' } },
  },
  {
    type: 'group',
    label: '文库小说',
    children: favoredList.value.wenku.map(({ id, title }) =>
      favoriteMenuOption('wenku', id, title)
    ),
  },
]);

const showListModal = ref(false);
const showAddModal = ref(false);
</script>

<template>
  <div style="display: flex">
    <div style="flex: auto">
      <n-button
        v-if="!isDesktop"
        @click="showListModal = true"
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
      :value="currentMenuKey"
      :options="menuOptions"
      style="flex: none; width: 250px"
    />
  </div>

  <n-drawer v-model:show="showListModal" placement="right">
    <n-drawer-content :native-scrollbar="false" max-width="600">
      <n-menu :value="currentMenuKey" :options="menuOptions" />
    </n-drawer-content>
  </n-drawer>

  <favorite-add-modal v-model:show="showAddModal" @created="loadFavoredList" />
</template>
