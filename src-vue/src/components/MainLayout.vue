<script lang="ts" setup>
import { h } from 'vue';
import { MenuOption } from 'naive-ui';
import { MenuFilled } from '@vicons/material';
import { useRoute } from 'vue-router';

const path = useRoute().path;

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const topMenuOptions: MenuOption[] = [
  menuOption('首页', '/'),
  menuOption('列表', '/list'),
  menuOption('编辑历史', '/patch'),
];

const collapsedMenuOptions: MenuOption[] = [
  menuOption('首页', '/'),
  {
    label: '列表',
    children: [
      menuOption('已缓存小说', '/list'),
      menuOption('成为小说家：流派', '/rank/syosetu/1'),
      menuOption('成为小说家：综合', '/rank/syosetu/2'),
      menuOption('成为小说家：异世界转移/转生', '/rank/syosetu/3'),
    ],
  },
  menuOption('编辑历史', '/patch'),
];

function getTopMenuOptionKey() {
  if (path.startsWith('/patch')) {
    return '/patch';
  } else if (path.startsWith('/list') || path.startsWith('/rank')) {
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
        <n-menu :value="path" :options="collapsedMenuOptions" />
      </n-popover>
    </n-layout-header>
    <slot />
  </n-layout>
</template>
