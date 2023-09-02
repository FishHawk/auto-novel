<script lang="ts" setup>
import { ApiUser } from '@/data/api/api_user';
import { mapOk } from '@/data/api/result';
import { useAuthInfoStore } from '@/data/stores/authInfo';

import { Loader } from './components/NovelList.vue';

const authInfoStore = useAuthInfoStore();
const loader: Loader = (page, _query, _selected) => {
  return ApiUser.listReadHistoryWebNovel(page - 1, 10).then((result) =>
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
