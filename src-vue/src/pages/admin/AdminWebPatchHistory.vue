<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import {
  ApiWebNovelHistory,
  WebNovelPatchHistoryDto,
  WebNovelPatchHistoryOutlineDto,
} from '@/data/api/api_web_novel_history';
import { Page } from '@/data/api/page';

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const novelPage = ref<ResultState<Page<WebNovelPatchHistoryOutlineDto>>>();
const details = ref<{ [key: string]: WebNovelPatchHistoryDto }>({});

async function loadPage(page: number) {
  novelPage.value = undefined;
  const result = await ApiWebNovelHistory.listPatch(currentPage.value - 1);
  if (currentPage.value == page) {
    novelPage.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

async function loadPatch(providerId: string, novelId: string) {
  const result = await ApiWebNovelHistory.getPatch(providerId, novelId);
  if (result.ok) {
    details.value[`${providerId}/${novelId}`] = result.value;
  }
}

async function deletePatch(providerId: string, novelId: string) {
  const result = await ApiWebNovelHistory.deletePatch(providerId, novelId);
  if (result.ok) {
    message.info('删除成功');
    if (novelPage.value?.ok) {
      novelPage.value.value.items = novelPage.value.value.items.filter(
        (it) => it.providerId !== providerId || it.novelId !== novelId
      );
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function revokePatch(providerId: string, novelId: string) {
  const result = await ApiWebNovelHistory.revokePatch(providerId, novelId);
  if (result.ok) {
    message.info('撤销成功');
    if (novelPage.value?.ok) {
      novelPage.value.value.items = novelPage.value.value.items.filter(
        (it) => it.providerId !== providerId || it.novelId !== novelId
      );
    }
  } else {
    message.error('撤销失败：' + result.error.message);
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <AdminLayout>
    <n-h1>网页编辑历史</n-h1>
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
          {{ item.titleJp }}
          <br />
          {{ item.titleZh }}
        </n-p>
        <template v-if="`${item.providerId}/${item.novelId}` in details">
          <div
            v-for="p of details[`${item.providerId}/${item.novelId}`].patches"
          >
            <n-p prefix="bar">{{ p.uuid }}</n-p>
            <n-space vertical>
              <TextDiff v-if="p.titleChange" :diff="p.titleChange" />
              <TextDiff
                v-if="p.introductionChange"
                :diff="p.introductionChange"
              />
              <TextDiff v-for="textChange of p.tocChange" :diff="textChange" />
              <table style="border-spacing: 16px 0px">
                <tr v-for="(termZh, termJp) in p.glossary">
                  <td>{{ termJp }}</td>
                  <td style="width: 4px">=></td>
                  <td>{{ termZh }}</td>
                </tr>
              </table>
            </n-space>
          </div>

          <n-space>
            <n-button @click="deletePatch(item.providerId, item.novelId)">
              删除
            </n-button>
            <n-button @click="revokePatch(item.providerId, item.novelId)">
              撤销
            </n-button>
          </n-space>
        </template>
        <n-button v-else @click="loadPatch(item.providerId, item.novelId)">
          加载
        </n-button>
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
