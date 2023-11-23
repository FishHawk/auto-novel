<script lang="ts" setup>
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { Page } from '@/data/api/common';

import { Loader } from '../list/components/NovelList.vue';

const loader: Loader<Page<WebNovelOutlineDto>> = (page, _query, _selected) =>
  ApiWebNovel.listReadHistory({ page, pageSize: 30 });
</script>

<template>
  <AccountLayout>
    <NovelList :search="false" :options="[]" :loader="loader" v-slot="{ page }">
      <NovelListWeb :items="page.items" simple />
    </NovelList>
  </AccountLayout>
</template>
