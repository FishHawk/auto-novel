<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import ApiTocMergeHistory, {
  TocMergeHistoryDto,
  TocMergeHistoryPageDto,
} from '@/data/api/api_toc_merge_history';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const message = useMessage();
const auth = useAuthInfoStore();

const currentPage = ref(1);
const total = ref(1);
const bookPage = ref<ResultState<TocMergeHistoryPageDto>>();
const details = ref<{ [key: string]: DiffTocItem[] }>({});

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await ApiTocMergeHistory.listTocMergeHistory(
    currentPage.value - 1
  );
  if (currentPage.value == page) {
    bookPage.value = result;
    if (result.ok) {
      total.value = result.value.total;
    }
  }
}

async function loadDetail(id: string) {
  const result = await ApiTocMergeHistory.getTocMergeHistory(id);
  if (result.ok) {
    details.value[id] = diffToc(result.value);
  }
}

async function deleteDetail(id: string) {
  const result = await ApiTocMergeHistory.deleteMergeHistory(id, auth.token!);
  if (result.ok) {
    message.info('删除成功');
    if (bookPage.value?.ok) {
      bookPage.value.value.items = bookPage.value.value.items.filter(
        (it) => it.id !== id
      );
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

interface DiffTocItem {
  same: boolean;
  oldV?: { titleJp: string; episodeId?: string };
  newV?: { titleJp: string; episodeId?: string };
}

function diffToc(detail: TocMergeHistoryDto): DiffTocItem[] {
  const maxLength = Math.max(detail.tocOld.length, detail.tocNew.length);
  const diffItems: DiffTocItem[] = [];
  let firstDiff = false;
  console.log(detail);
  for (let i = 0; i < maxLength; i++) {
    const oldV = detail.tocOld[i];
    const newV = detail.tocNew[i];
    const diff =
      oldV === undefined ||
      newV === undefined ||
      oldV.titleJp !== newV.titleJp ||
      oldV.episodeId !== newV.episodeId;
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
    <n-h1>目录合并历史</n-h1>
    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      :page-slot="7"
    />
    <n-divider />
    <div v-if="bookPage?.ok">
      <div v-for="item in bookPage.value.items">
        <n-p>
          <n-a
            :href="`/novel/${item.providerId}/${item.bookId}`"
            target="_blank"
          >
            {{ `${item.providerId}/${item.bookId}` }}
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
              {{ t.oldV?.episodeId }}
            </td>
            <td :style="{ color: t.same ? 'grey' : 'red' }">
              {{ t.newV?.titleJp }}
              <br />
              {{ t.newV?.episodeId }}
            </td>
          </tr>
          <n-button @click="deleteDetail(item.id)">删除</n-button>
        </table>
        <n-button v-else @click="loadDetail(item.id)">加载</n-button>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="Math.floor(total / 10)"
      :page-slot="7"
    />
  </AdminLayout>
</template>
