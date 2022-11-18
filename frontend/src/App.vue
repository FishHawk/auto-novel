<script lang="ts" setup>
import { computed } from 'vue';
import PageQuery from './pages/PageQuery.vue';
import PageList from './pages/PageList.vue';

const routes: { [id: string]: typeof PageQuery } = {
  '/': PageQuery,
  '/list': PageList,
};

let currentPath = window.location.hash;
const currentView = computed(() => {
  return routes[currentPath.slice(1) || '/'];
});
window.addEventListener('hashchange', () => {
  currentPath = window.location.hash;
});
</script>

<template>
  <component :is="currentView" />
</template>
