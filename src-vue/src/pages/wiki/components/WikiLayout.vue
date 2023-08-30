<script lang="ts" setup>
import { menuOption } from '@/components/MainLayout.vue';
const menuOptions = [
  menuOption('如何使用插件翻译', '/wiki/extension'),
  menuOption('如何使用搜索', '/wiki/search'),
  menuOption('反馈', '/wiki/feedback'),
  menuOption('赞助', '/wiki/donate'),
];
</script>

<template>
  <MainLayout :side-menu-options="menuOptions">
    <slot />
  </MainLayout>
</template>
