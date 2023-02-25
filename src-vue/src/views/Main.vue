<script lang="ts" setup>
import { h } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { MenuOption } from 'naive-ui';
import { MenuFilled } from '@vicons/material';

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
  {
    label: () =>
      h(RouterLink, { to: { path: '/patch' } }, { default: () => '编辑历史' }),
    key: '/patch',
  },
];

const collapsedMenuOptions: MenuOption[] = [
  {
    label: () =>
      h(RouterLink, { to: { path: '/' } }, { default: () => '首页' }),
    key: '/1',
  },
  {
    label: '列表',
    children: [
      {
        label: () =>
          h(
            RouterLink,
            { to: { path: '/list' } },
            { default: () => '已缓存小说' }
          ),
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
    ],
  },
  {
    label: () =>
      h(RouterLink, { to: { path: '/patch' } }, { default: () => '编辑历史' }),
    key: '/patch',
  },
];

const route = useRoute();
function getTopMenuOptionKey() {
  if (route.path.startsWith('/patch')) {
    return '/patch';
  } else if (route.path.startsWith('/list') || route.path.startsWith('/rank')) {
    return '/list';
  } else {
    return '/';
  }
}
</script>

<template>
  <n-layout>
    <n-layout-header
      bordered
      style="display: flex; align-items: center; height: 50px"
    >
      <n-menu
        :value="getTopMenuOptionKey()"
        mode="horizontal"
        :options="topMenuOptions"
        style="flex: 1"
      />
      <n-popover trigger="click" :width="280" style="padding: 0">
        <template #trigger>
          <n-icon size="24" class="on-mobile" style="padding-inline-end: 16px">
            <MenuFilled />
          </n-icon>
        </template>
        <n-menu v-model:value="$route.path" :options="collapsedMenuOptions" />
      </n-popover>
    </n-layout-header>
    <router-view />
  </n-layout>
</template>
