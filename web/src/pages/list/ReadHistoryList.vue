<script lang="ts" setup>
import { ApiUser } from '@/data/api/api_user';
import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { Page } from '@/data/api/common';

import { Loader } from '../list/components/NovelList.vue';

const loader: Loader<Page<WebNovelOutlineDto>> = (page, _query, _selected) =>
  ApiUser.listReadHistoryWeb({ page, pageSize: 30 });
</script>

<template>
  <div class="layout-content">
    <n-h1>阅读历史</n-h1>
    <NovelList :options="[]" :loader="loader" v-slot="{ page }">
      <NovelListWeb :items="page.items" simple />
    </NovelList>
  </div>
</template>
