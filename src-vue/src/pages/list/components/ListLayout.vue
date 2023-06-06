<script lang="ts" setup>
import { computed, h } from 'vue';
import { MenuOption } from 'naive-ui';
import { useRoute } from 'vue-router';

import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const path = useRoute().path;

function menuOption(text: string, href: string, show?: boolean): MenuOption {
  return { label: () => h('a', { href }, text), key: href, show };
}

const menuOptions = computed(() => {
  const signed = authInfoStore.info !== undefined;
  return [
    menuOption('我的收藏', '/favorite-list', signed),
    menuOption('阅读历史', '/read-history', signed),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
    menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
    menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
  ];
});
</script>

<template>
  <MainLayout>
    <n-layout has-sider>
      <n-layout-content id="list-container">
        <slot />
      </n-layout-content>
      <n-layout-sider class="on-desktop">
        <n-menu :value="path" :options="menuOptions" />
      </n-layout-sider>
    </n-layout>
  </MainLayout>
</template>

<style scoped>
@media only screen and (min-width: 600px) {
  #list-container {
    margin-right: 12px;
  }
}
</style>
