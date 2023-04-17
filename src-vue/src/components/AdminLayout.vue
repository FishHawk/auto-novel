<script lang="ts" setup>
import { h } from 'vue';
import { MenuOption } from 'naive-ui';
import { useRoute } from 'vue-router';

const path = useRoute().path;

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const menuOptions: MenuOption[] = [
  menuOption('编辑历史', '/admin/patch'),
  menuOption('目录合并历史', '/admin/toc-merge'),
];
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
