<script lang="ts" setup>
import ApiWebNovel from '@/data/api/api_web_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();
async function loader(page: number) {
  return ApiWebNovel.listFavorite(page - 1, 10, authInfoStore.token!);
}
</script>

<template>
  <ListLayout>
    <n-h1>我的收藏</n-h1>
    <WebBookList :search="false" :options="[]" :loader="loader" />
  </ListLayout>
</template>
