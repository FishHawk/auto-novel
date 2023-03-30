<script lang="ts" setup>
import { h } from 'vue';
import { MenuOption } from 'naive-ui';
import { useRoute } from 'vue-router';

const path = useRoute().path;

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const menuOptions: MenuOption[] = [
  menuOption('网络小说', '/novel-list'),
  menuOption('文库小说', '/wenku-list'),
  menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
  menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
  menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
];
</script>

<template>
  <MainLayout>
    <n-layout has-sider>
      <n-layout-content id="list-container">
        <div>
          <slot />
        </div>
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
