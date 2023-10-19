<script lang="ts" setup>
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { Page } from '@/data/api/page';

import { Loader } from './components/NovelList.vue';

const loader: Loader<Page<WenkuNovelOutlineDto>> = (page, query) =>
  ApiWenkuNovel.list({ page, pageSize: 24, query });
</script>

<template>
  <ListLayout>
    <n-h1>文库小说</n-h1>
    <RouterNA to="/wenku-edit">新建文库小说</RouterNA>
    <NovelList :search="true" :options="[]" :loader="loader" v-slot="{ page }">
      <NovelListWenku :items="page.items" />
    </NovelList>
  </ListLayout>
</template>

<style scoped>
.n-card-header__main {
  text-overflow: ellipsis;
}
</style>
