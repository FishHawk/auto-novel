<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import ApiPatch, { BookPatchPageDto, BookPatchDto } from '@/data/api/api_patch';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const message = useMessage();
const auth = useAuthInfoStore();

const currentPage = ref(1);
const total = ref(1);
const bookPage = ref<ResultState<BookPatchPageDto>>();
const details = ref<{ [key: string]: BookPatchDto }>({});

async function loadPage(page: number) {
  bookPage.value = undefined;
  const result = await ApiPatch.listPatch(currentPage.value - 1);
  if (currentPage.value == page) {
    bookPage.value = result;
    if (result.ok) {
      total.value = result.value.total;
    }
  }
}

async function loadPatch(providerId: string, bookId: string) {
  const result = await ApiPatch.getPatch(providerId, bookId);
  if (result.ok) {
    details.value[`${providerId}/${bookId}`] = result.value;
  }
}

async function deletePatch(providerId: string, bookId: string) {
  const result = await ApiPatch.deletePatch(providerId, bookId, auth.token!);
  if (result.ok) {
    message.info('删除成功');
    if (bookPage.value?.ok) {
      bookPage.value.value.items = bookPage.value.value.items.filter(
        (it) => it.providerId !== providerId || it.bookId !== bookId
      );
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function revokePatch(providerId: string, bookId: string) {
  const result = await ApiPatch.revokePatch(providerId, bookId, auth.token!);
  if (result.ok) {
    message.info('撤销成功');
    if (bookPage.value?.ok) {
      // bookPage.value.value.items = bookPage.value.value.items.filter(
      //   (it) => it.providerId !== providerId || it.bookId !== bookId
      // );
    }
  } else {
    message.error('撤销失败：' + result.error.message);
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <AdminLayout>
    <n-h1>编辑历史</n-h1>
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
          {{ item.titleJp }}
          <br />
          {{ item.titleZh }}
        </n-p>
        <template v-if="`${item.providerId}/${item.bookId}` in details">
          <div
            v-for="p of details[`${item.providerId}/${item.bookId}`].patches"
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

          <div
            v-for="[episodeId, episodePatches] of Object.entries(
              details[`${item.providerId}/${item.bookId}`].toc
            )"
          >
            <n-h2 prefix="bar">{{ episodePatches.titleJp }}</n-h2>
            <div v-for="episodePatch in episodePatches.patches">
              <n-h4 prefix="bar">{{ episodePatch.uuid }}</n-h4>
              <n-space vertical>
                <TextDiff
                  v-for="textChange of episodePatch.paragraphsChange"
                  :diff="textChange"
                />
              </n-space>
              <n-divider />
            </div>
          </div>
          <n-space>
            <n-button @click="deletePatch(item.providerId, item.bookId)">
              删除
            </n-button>
            <n-button @click="revokePatch(item.providerId, item.bookId)">
              撤销
            </n-button>
          </n-space>
        </template>
        <n-button v-else @click="loadPatch(item.providerId, item.bookId)">
          加载
        </n-button>
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
