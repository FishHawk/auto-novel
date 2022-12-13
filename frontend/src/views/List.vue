<script lang="ts" setup>
import { Ref, ref, watch } from 'vue';

import { Result } from '../models/util';
import {
  BookPagedList,
  getBookPagedList,
  filenameToUrl,
} from '../models/book_storage';

const currentPage = ref(1);
const total = ref(1);
const books: Ref<Result<BookPagedList, any> | undefined> = ref();

async function loadPage(page: number) {
  books.value = undefined;
  const pagedList = await getBookPagedList(currentPage.value);
  if (currentPage.value == page) {
    books.value = pagedList;
    if (pagedList.ok) {
      total.value = pagedList.value.total;
    }
  }
}
watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <div class="content">
    <n-pagination
      v-model:page="currentPage"
      :page-count="total / 10"
      show-quick-jumper
    />
    <n-divider />
    <div class="list" v-if="books?.ok">
      <div v-for="book in books.value.books">
        <h1 class="title">
          <n-a
            :href="`/novel/${book.provider_id}/${book.book_id}`"
            target="_blank"
          >
            {{ book.title }}
          </n-a>
        </h1>
        <n-a :href="book.url" target="_blank">
          {{ book.url }}
        </n-a>

        <n-space v-for="group in book.files" :key="group.lang">
          <span>
            {{
              group.lang +
              '(' +
              group.cached_episode_number +
              '/' +
              group.total_episode_number +
              ')'
            }}
          </span>

          <n-space spacer="|">
            <n-a
              v-for="file in group.files"
              :href="filenameToUrl(file.filename)"
              target="_blank"
            >
              {{ file.type.toUpperCase() }}
            </n-a>
            <n-a
              v-for="file in group.files"
              :href="filenameToUrl(file.filename)"
              target="_blank"
            >
              原文对比版{{ file.type.toUpperCase() }}
            </n-a>
          </n-space>
        </n-space>
        <n-divider />
      </div>
    </div>
    <n-pagination
      v-model:page="currentPage"
      :page-count="total / 10"
      show-quick-jumper
    />
  </div>
</template>

<style>
.list {
  min-height: 600px;
}
</style>
