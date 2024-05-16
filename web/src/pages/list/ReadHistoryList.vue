<script lang="ts" setup>
import { UserRepository } from '@/data/api';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { runCatching } from '@/util/result';

import { Loader } from '../list/components/NovelPage.vue';
import { doAction } from '../util';

defineProps<{
  page: number;
}>();

const message = useMessage();

const loader: Loader<WebNovelOutlineDto> = (page, _query, _selected) =>
  runCatching(UserRepository.listReadHistoryWeb({ page, pageSize: 30 }));

const deleteHistory = (providerId: string, novelId: string) =>
  doAction(
    UserRepository.deleteReadHistoryWeb(providerId, novelId).then(() => {
      window.location.reload();
    }),
    '删除',
    message,
  );
</script>

<template>
  <div class="layout-content">
    <n-h1>阅读历史</n-h1>

    <novel-page :page="page" :loader="loader" :options="[]" v-slot="{ items }">
      <novel-list-web :items="items" simple>
        <template #action="item">
          <c-button
            size="tiny"
            label="删除"
            style="margin-top: 2px"
            @action="deleteHistory(item.providerId, item.novelId)"
          />
        </template>
      </novel-list-web>
    </novel-page>
  </div>
</template>
