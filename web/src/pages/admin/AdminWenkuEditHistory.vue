<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';
import { isEqual } from 'lodash-es';

import { ResultState } from '@/data/api/result';
import {
  ApiWenkuNovelHistory,
  WenkuEditHistory,
} from '@/data/api/api_wenku_novel_history';
import { Page } from '@/data/api/page';

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const historiesResult = ref<ResultState<Page<WenkuEditHistory>>>();

async function loadPage(page: number) {
  historiesResult.value = undefined;
  const result = await ApiWenkuNovelHistory.listEditHistory(
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
  const result = await ApiWenkuNovelHistory.deleteEditHistory(id);
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
    <n-h1>文库编辑历史</n-h1>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <n-divider />
    <ResultView
      :result="historiesResult"
      :showEmpty="(it: Page<WenkuEditHistory>) => it.items.length === 0"
      v-slot="{ value: histories }"
    >
      <div v-for="item in histories.items">
        <n-thing>
          <template #header>
            <RouterNA :to="`/wenku/${item.novelId}`">
              {{ item.novelId }}
            </RouterNA>
          </template>
          <template #description>
            <b>{{ item.operator }}</b>
            于<n-time :time="item.createAt * 1000" type="relative" />上传
          </template>
          <template #header-extra>
            <n-button @click="deleteHistory(item.id)">删除</n-button>
          </template>

          <n-p v-if="item.old?.title !== item.new.title">
            标题：{{ item.old?.title }} => {{ item.new.title }}
          </n-p>
          <n-p v-if="item.old?.titleZh !== item.new.titleZh">
            中文标题：{{ item.old?.titleZh }} => {{ item.new.titleZh }}
          </n-p>
          <n-p v-if="!isEqual(item.old?.authors, item.new.authors)">
            作者：{{ item.old?.authors }} => {{ item.new.authors }}
          </n-p>
          <n-p v-if="!isEqual(item.old?.artists, item.new.artists)">
            插图：{{ item.old?.artists }} => {{ item.new.artists }}
          </n-p>
          <n-p v-if="item.old?.introduction !== item.new.introduction">
            简介：{{ item.old?.introduction }} => {{ item.new.introduction }}
          </n-p>
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
