<script lang="ts" setup>
import { computed, h } from 'vue';
import { MenuOption } from 'naive-ui';
import { useRoute } from 'vue-router';

import { useAuthInfoStore } from '@/data/stores/authInfo';
import { useIsDesktop } from '@/data/util';

const isDesktop = useIsDesktop(850);
const authInfoStore = useAuthInfoStore();

const path = useRoute().path;

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const menuOptions = computed(() => {
  const signed = authInfoStore.info !== undefined;
  return [
    menuOption('如何使用插件翻译', '/wiki/extension'),
    menuOption('如何使用搜索', '/wiki/search'),
  ];
});
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
