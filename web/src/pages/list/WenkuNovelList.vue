<script lang="ts" setup>
import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { mapOk } from '@/data/api/result';

import { Loader } from './components/NovelList.vue';

const loader: Loader = (page: number, query: string, _selected: number[]) => {
  return ApiWenkuNovel.list(page - 1, query).then((result) =>
    mapOk(result, (page) => ({ type: 'wenku', page }))
  );
};
</script>

<template>
  <ListLayout>
    <n-h1>文库小说</n-h1>
    <RouterNA to="/wenku-edit">新建文库小说</RouterNA>
    <NovelList :search="true" :options="[]" :loader="loader" />
  </ListLayout>
</template>

<style scoped>
.n-card-header__main {
  text-overflow: ellipsis;
}
</style>
