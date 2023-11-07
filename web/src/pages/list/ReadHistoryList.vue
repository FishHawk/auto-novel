<script lang="ts" setup>
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { Page } from '@/data/api/common';

import { Loader } from './components/NovelList.vue';

const loader: Loader<Page<WebNovelOutlineDto>> = (page, _query, _selected) =>
  ApiWebNovel.listReadHistory({ page, pageSize: 30 });
</script>

<template>
  <MainLayout>
    <n-h1>阅读历史</n-h1>
    <NovelList :search="false" :options="[]" :loader="loader" v-slot="{ page }">
      <NovelListWeb :items="page.items" simple />
    </NovelList>
  </MainLayout>
</template>
