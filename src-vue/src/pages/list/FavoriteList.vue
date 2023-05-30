<script lang="ts" setup>
import ApiUser from '@/data/api/api_user';
import { mapOk } from '@/data/api/result';
import { useAuthInfoStore } from '@/data/stores/authInfo';

import { Loader } from './components/NovelList.vue';

const options = [
  {
    label: '类型',
    tags: ['网页小说', '文库小说'],
  },
];

const authInfoStore = useAuthInfoStore();
const loader: Loader = (page, _query, selected) => {
  function optionNth(n: number): string {
    return options[n].tags[selected[n]];
  }
  if (optionNth(0) === '网页小说') {
    return ApiUser.listFavoritedWebNovel(
      page - 1,
      10,
      authInfoStore.token!
    ).then((result) => mapOk(result, (page) => ({ type: 'web', page })));
  } else {
    return ApiUser.listFavoritedWenkuNovel(
      page - 1,
      24,
      authInfoStore.token!
    ).then((result) => mapOk(result, (page) => ({ type: 'wenku', page })));
  }
};
</script>

<template>
  <ListLayout>
    <n-h1>我的收藏</n-h1>
    <NovelList :search="false" :options="options" :loader="loader" />
  </ListLayout>
</template>
