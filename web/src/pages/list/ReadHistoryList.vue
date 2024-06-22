<script lang="ts" setup>
import { DeleteOutlineOutlined } from '@vicons/material';

import { WebNovelOutlineDto } from '@/model/WebNovel';
import { runCatching } from '@/util/result';

import { Loader } from '../list/components/NovelPage.vue';
import { doAction } from '../util';
import { Locator } from '@/data';

defineProps<{
  page: number;
}>();

const message = useMessage();

const userRepository = Locator.userRepository;

const loader: Loader<WebNovelOutlineDto> = (page, _query, _selected) =>
  runCatching(userRepository.listReadHistoryWeb({ page, pageSize: 30 }));

const clearHistory = () =>
  doAction(
    userRepository.clearReadHistoryWeb().then(() => {
      window.location.reload();
    }),
    '清空',
    message,
  );

const deleteHistory = (providerId: string, novelId: string) =>
  doAction(
    userRepository.deleteReadHistoryWeb(providerId, novelId).then(() => {
      window.location.reload();
    }),
    '删除',
    message,
  );
</script>

<template>
  <div class="layout-content">
    <n-h1>阅读历史</n-h1>

    <n-flex style="margin-bottom: 24px">
      <c-button
        label="清空记录"
        :icon="DeleteOutlineOutlined"
        @action="clearHistory()"
      />
    </n-flex>

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
