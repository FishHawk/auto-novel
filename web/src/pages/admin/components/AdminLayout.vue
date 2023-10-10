<script lang="ts" setup>
import { h } from 'vue';
import { MenuOption } from 'naive-ui';
import { useRoute } from 'vue-router';

import { useIsDesktop } from '@/data/util';

const isDesktop = useIsDesktop(850);
const path = useRoute().path;

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const menuOptions: MenuOption[] = [
  menuOption('网页编辑历史', '/admin/web-patch-history'),
  menuOption('网页目录合并历史', '/admin/web-toc-merge-history'),
];
</script>

<template>
  <MainLayout>
    <n-layout has-sider>
      <n-layout-content>
        <slot />
      </n-layout-content>
      <n-layout-sider v-if="isDesktop" style="margin-left: 12px">
        <n-menu :value="path" :options="menuOptions" />
      </n-layout-sider>
    </n-layout>
  </MainLayout>
</template>
