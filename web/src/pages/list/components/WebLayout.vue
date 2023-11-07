<script lang="ts" setup>
import { menuOption } from '@/components/MainLayout.vue';

const menuOptions = [
  menuOption('网络小说', '/novel-list'),
  menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
  menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
  menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
  menuOption('Kakuyomu：流派', '/novel-rank/kakuyomu/1'),
];
</script>

<template>
  <MainLayout :side-menu-options="menuOptions">
    <slot />
  </MainLayout>
</template>
