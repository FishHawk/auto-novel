<script lang="ts" setup>
import { h } from 'vue';
import { MenuOption } from 'naive-ui';
import { useRoute } from 'vue-router';

const path = useRoute().path;

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const menuOptions: MenuOption[] = [
  menuOption('已缓存小说', '/list'),
  menuOption('成为小说家：流派', '/rank/syosetu/1'),
  menuOption('成为小说家：综合', '/rank/syosetu/2'),
  menuOption('成为小说家：异世界转移/转生', '/rank/syosetu/3'),
];
</script>

<template>
  <MainLayout>
    <n-layout has-sider>
      <n-layout-sider bordered class="on-desktop">
        <n-menu :value="path" :options="menuOptions" />
      </n-layout-sider>
      <n-layout-content> <slot /> </n-layout-content>
    </n-layout>
  </MainLayout>
</template>
