<script lang="ts" setup>
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { mapOk } from '@/data/api/result';

import { Loader } from './components/NovelList.vue';

const loader: Loader = (page, _query, _selected) => {
  return ApiWebNovel.listReadHistory(page - 1, 10).then((result) =>
    mapOk(result, (page) => ({ type: 'web', page }))
  );
};
</script>

<template>
  <ListLayout>
    <n-h1>阅读历史</n-h1>
    <NovelList :loader="loader" />
  </ListLayout>
</template>
