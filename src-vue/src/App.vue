<script lang="ts" setup>
import { zhCN, dateZhCN } from 'naive-ui';

import { updateToken } from './data/api/api';
import { useUserDataStore } from './data/stores/userData';

useUserDataStore().$subscribe(
  (_mutation, state) => updateToken(state.info?.token),
  { detached: true, immediate: true }
);
</script>

<template>
  <n-config-provider
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
  >
    <n-message-provider>
      <router-view :key="$route.path" />
    </n-message-provider>
  </n-config-provider>
</template>

<style>
body {
  font-family: 'IBMPlexSans', Arial, sans-serif;
}
a {
  text-decoration: none;
}
p,
li {
  overflow-wrap: break-word;
  word-break: break-word;
}
.n-h:first-child {
  margin: var(--n-margin);
}
.n-layout {
  overflow: visible;
}
.n-layout .n-layout-scroll-container {
  overflow-x: visible;
}
.n-scrollbar-rail {
  z-index: 10000;
}
.text-2line {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
