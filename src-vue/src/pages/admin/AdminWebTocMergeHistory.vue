<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import {
  ApiWebNovelHistory,
  TocMergeHistoryDto,
  TocMergeHistoryOutlineDto,
} from '@/data/api/api_web_novel_history';
import { useAuthInfoStore } from '@/data/stores/authInfo';
import { Page } from '@/data/api/page';

const message = useMessage();
const auth = useAuthInfoStore();

const currentPage = ref(1);
const pageNumber = ref(1);
const novelPage = ref<ResultState<Page<TocMergeHistoryOutlineDto>>>();
const details = ref<{ [key: string]: DiffTocItem[] }>({});

async function loadPage(page: number) {
  novelPage.value = undefined;
  const result = await ApiWebNovelHistory.listTocMergeHistory(
    currentPage.value - 1
  );
  if (currentPage.value == page) {
    novelPage.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

async function loadDetail(id: string) {
  const result = await ApiWebNovelHistory.getTocMergeHistory(id);
  if (result.ok) {
    details.value[id] = diffToc(result.value);
  }
}

async function deleteDetail(id: string) {
  const result = await ApiWebNovelHistory.deleteMergeHistory(id, auth.token!);
  if (result.ok) {
    message.info('删除成功');
    if (novelPage.value?.ok) {
      novelPage.value.value.items = novelPage.value.value.items.filter(
        (it) => it.id !== id
      );
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

interface DiffTocItem {
  same: boolean;
  oldV?: { titleJp: string; chapterId?: string };
  newV?: { titleJp: string; chapterId?: string };
}

function diffToc(detail: TocMergeHistoryDto): DiffTocItem[] {
  const maxLength = Math.max(detail.tocOld.length, detail.tocNew.length);
  const diffItems: DiffTocItem[] = [];
  let firstDiff = false;
  for (let i = 0; i < maxLength; i++) {
    const oldV = detail.tocOld[i];
    const newV = detail.tocNew[i];
    const diff =
      oldV === undefined ||
      newV === undefined ||
      oldV.titleJp !== newV.titleJp ||
      oldV.chapterId !== newV.chapterId;
    if (diff) {
      firstDiff = diff;
    }
    if (firstDiff) {
      diffItems.push({ same: !diff, oldV, newV });
    }
  }
  return diffItems;
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <AdminLayout>
    <n-h1>网页目录合并历史</n-h1>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <n-divider />
    <div v-if="novelPage?.ok">
      <div v-for="item in novelPage.value.items">
        <n-p>
          <n-a
            :href="`/novel/${item.providerId}/${item.novelId}`"
            target="_blank"
          >
            {{ `${item.providerId}/${item.novelId}` }}
          </n-a>
          <br />
          {{ item.reason }}
        </n-p>
        <table v-if="item.id in details">
          <tr>
            <th>旧目录</th>
            <th>新目录</th>
          </tr>
          <tr v-for="t of details[item.id]">
            <td :style="{ color: t.same ? 'grey' : 'red' }">
              {{ t.oldV?.titleJp }}
              <br />
              {{ t.oldV?.chapterId }}
            </td>
            <td :style="{ color: t.same ? 'grey' : 'red' }">
              {{ t.newV?.titleJp }}
              <br />
              {{ t.newV?.chapterId }}
            </td>
          </tr>
          <n-button @click="deleteDetail(item.id)">删除</n-button>
        </table>
        <n-button v-else @click="loadDetail(item.id)">加载</n-button>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
  </AdminLayout>
</template>
