<script lang="ts" setup>
import { UserRepository } from '@/data/api';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { runCatching } from '@/util/result';
import { SearchOutlined } from '@vicons/material';

import { Loader } from '../list/components/NovelPage.vue';
import { doAction } from '../util';

defineProps<{
  page: number;
}>();

const query = ref('');
const message = useMessage();

const loader: Loader<WebNovelOutlineDto> = (page, _query, _selected) =>
  runCatching(
    UserRepository.listReadHistoryWeb({
      query: query.value,
      page,
      pageSize: 30,
    }),
  );

const deleteHistory = (providerId: string, novelId: string) =>
  doAction(
    UserRepository.deleteReadHistoryWeb(providerId, novelId).then(() => {
      window.location.reload();
    }),
    '删除',
    message,
  );

const stopRecordHistory = () =>
  doAction(
    UserRepository.stopReadHistoryWeb(),
    '暂停记录历史',
    message,
  );
const clearHistory = () =>
  doAction(
    UserRepository.clearReadHistoryWeb().then(() => {
      window.location.reload();
    }),
    '清空历史',
    message,
  );
</script>
<template>
  <div class="layout-content">
    <section-header title="阅读历史">
      <n-form inline size="small">
        <n-form-item>
          <n-input
            clearable
            size="small"
            placeholder="搜索历史记录"
            round
            v-model:value="query"
            type="text"
            style="width: 200px"
          >
            <template #prefix>
              <n-icon :component="SearchOutlined" />
            </template>
          </n-input>
        </n-form-item>
        <n-form-item>
          <n-space>
            <c-button
              label="暂停记录历史"
              size="small"
              @action="stopRecordHistory"
            ></c-button>
            <c-button
              label="清空历史"
              size="small"
              @action="clearHistory"
            ></c-button>
          </n-space>
        </n-form-item>
      </n-form>
    </section-header>
    <n-timeline>
      <novel-page
        :page="page"
        :loader="loader"
        :options="[]"
        v-slot="{ items }"
      >
        <read-history-novel-list-web :items="items">
          <template #action="item">
            <c-button
              size="small"
              label="删除"
              style="align-items: center"
              @action="deleteHistory(item.providerId, item.novelId)"
            />
          </template>
        </read-history-novel-list-web>
      </novel-page>
    </n-timeline>
  </div>
</template>
