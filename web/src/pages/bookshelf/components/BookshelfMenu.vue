<script lang="ts" setup>
import { MenuOption } from 'naive-ui';

import BookshelfMenuItem from './BookshelfMenuItem.vue';
import { useBookshelfStore } from '../BookshelfStore';

const message = useMessage();

const store = useBookshelfStore();

onMounted(async () => {
  try {
    await store.loadFavoredList();
  } catch (e) {
    message.error(`获取收藏列表失败：${e}`);
  }
});

const menuOption = (
  type: 'web' | 'wenku' | 'local',
  id: string,
  title: string,
): MenuOption => ({
  label: () => h(BookshelfMenuItem, { id, title, type }),
  key: `${type}/${id}`,
});

const menuOptions = computed(() => [
  {
    type: 'group',
    label: '网络小说',
    children: store.web.map(({ id, title }) => menuOption('web', id, title)),
  },
  {
    type: 'divider',
    key: 'divider',
    props: { style: { marginLeft: '32px' } },
  },
  {
    type: 'group',
    label: '文库小说',
    children: store.wenku.map(({ id, title }) =>
      menuOption('wenku', id, title),
    ),
  },
  {
    type: 'divider',
    key: 'divider',
    props: { style: { marginLeft: '32px' } },
  },
  {
    type: 'group',
    label: '本地小说',
    children: store.local.map(({ id, title }) =>
      menuOption('local', id, title),
    ),
  },
]);
</script>

<template>
  <n-menu :options="menuOptions" />
</template>
