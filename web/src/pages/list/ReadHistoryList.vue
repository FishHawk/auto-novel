<script lang="ts" setup>
import { UserRepository } from '@/data/api';
import { Page } from '@/model/Page';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { runCatching } from '@/util/result';

import { Loader } from '../list/components/NovelList.vue';
import { doAction } from '../util';

const message = useMessage();

const loader: Loader<Page<WebNovelOutlineDto>> = (page, _query, _selected) =>
  runCatching(UserRepository.listReadHistoryWeb({ page, pageSize: 30 }));

const deleteHistory = (providerId: string, novelId: string) =>
  doAction(
    UserRepository.deleteReadHistoryWeb(providerId, novelId).then(() => {
      window.location.reload();
    }),
    '删除',
    message
  );
</script>

<template>
  <div class="layout-content">
    <n-h1>阅读历史</n-h1>
    <NovelList :options="[]" :loader="loader" v-slot="{ page }">
      <NovelListWeb :items="page.items" simple>
        <template #action="item">
          <c-button
            size="tiny"
            label="删除"
            style="margin-top: 2px"
            @action="deleteHistory(item.providerId, item.novelId)"
          />
        </template>
      </NovelListWeb>
    </NovelList>
  </div>
</template>
