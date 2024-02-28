<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';

import { ApiOperation, MergeHistoryDto } from '@/data/api/api_operation';
import { Page } from '@/data/api/common';
import { Result} from '@/data/result';

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const novelPage = ref<Result<Page<MergeHistoryDto>>>();

async function loadPage(page: number) {
  novelPage.value = undefined;
  const result = await ApiOperation.listMergeHistory(currentPage.value - 1);
  if (currentPage.value == page) {
    novelPage.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

async function deleteDetail(id: string) {
  const result = await ApiOperation.deleteMergeHistory(id);
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

function diffToc(detail: MergeHistoryDto): DiffTocItem[] {
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
        <RouterNA :to="`/novel/${item.providerId}/${item.novelId}`">
          {{ `${item.providerId}/${item.novelId}` }}
        </RouterNA>
        <br />
        {{ item.reason }}
      </n-p>
      <table>
        <tr>
          <th>旧目录</th>
          <th>新目录</th>
        </tr>
        <tr v-for="t of diffToc(item)">
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
      <n-divider />
    </div>
  </div>
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
