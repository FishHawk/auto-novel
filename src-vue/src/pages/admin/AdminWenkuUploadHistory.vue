<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import {
  ApiWenkuNovelHistory,
  WenkuUploadHistory,
} from '@/data/api/api_wenku_novel_history';
import { Page } from '@/data/api/page';
import { readableDate } from '@/data/util';

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const historiesResult = ref<ResultState<Page<WenkuUploadHistory>>>();

async function loadPage(page: number) {
  historiesResult.value = undefined;
  const result = await ApiWenkuNovelHistory.listUploadHistory(
    currentPage.value - 1
  );
  if (currentPage.value == page) {
    historiesResult.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

async function deleteHistory(id: string) {
  const result = await ApiWenkuNovelHistory.deleteUploadHistory(id);
  if (result.ok) {
    message.info('删除成功');
    if (historiesResult.value?.ok) {
      historiesResult.value.value.items =
        historiesResult.value.value.items.filter((it) => it.id !== id);
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <AdminLayout>
    <n-h1>文库上传历史</n-h1>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <n-divider />
    <ResultView
      :result="historiesResult"
      :showEmpty="(it: Page<WenkuUploadHistory>) => it.items.length === 0"
      v-slot="{ value: histories }"
    >
      <div v-for="item in histories.items">
        <n-thing>
          <template #header>
            <n-a :href="`/wenku/${item.novelId}`" target="_blank">
              {{ item.novelId + '/' + item.volumeId }}
            </n-a>
          </template>
          <template #description>
            <b>{{ item.uploader }}</b>
            于{{ readableDate(item.createAt) }}上传
          </template>
          <template #header-extra>
            <n-button @click="deleteHistory(item.id)">删除</n-button>
          </template>
        </n-thing>
        <n-divider />
      </div>
    </ResultView>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
  </AdminLayout>
</template>
