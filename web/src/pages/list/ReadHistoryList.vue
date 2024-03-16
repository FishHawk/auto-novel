<script lang="ts" setup>
import { UserRepository } from '@/data/api';
import { Page } from '@/model/Page';
import { WebNovelOutlineDto } from '@/model/WebNovel';

import { Loader } from '../list/components/NovelList.vue';
import { runCatching } from '@/pages/result';

const loader: Loader<Page<WebNovelOutlineDto>> = (page, _query, _selected) =>
  runCatching(UserRepository.listReadHistoryWeb({ page, pageSize: 30 }));
</script>

<template>
  <div class="layout-content">
    <n-h1>阅读历史</n-h1>
    <NovelList :options="[]" :loader="loader" v-slot="{ page }">
      <NovelListWeb :items="page.items" simple />
    </NovelList>
  </div>
</template>
