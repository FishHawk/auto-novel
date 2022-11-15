<script lang="ts" setup>
import { onMounted, Ref, ref, watch } from 'vue';
import { Download } from '@element-plus/icons-vue';
import { Book, readableLang, filenameToUrl } from '../models/Book';
import axios from 'axios';
import { handleError } from '../models/Util';

interface PagedBooks {
  total: number;
  books: Book[];
}

const loading = ref(true);
const currentPage = ref(1);
const total = ref(1);
const books: Ref<Book[]> = ref([]);

function loadPage(page: number) {
  loading.value = true;
  books.value = [];
  axios
    .get('api/list', { params: { page: currentPage.value } })
    .then((res) => {
      if (currentPage.value == page) {
        loading.value = false;
        const pagedBooks = res.data as PagedBooks;
        total.value = pagedBooks.total;
        books.value = pagedBooks.books;
      }
    })
    .catch((error) => {
      if (currentPage.value == page) {
        handleError(error, '查询失败');
      }
    });
}

function onPageChange(page: number) {
  currentPage.value = page;
}

watch(currentPage, (page) => loadPage(page), { immediate: true });
</script>

<template>
  <el-pagination
    :current-page="currentPage"
    :total="total"
    layout="prev, pager, next, ->, jumper"
    @current-change="onPageChange"
  />
  <div class="list" v-loading="loading">
    <el-divider />
    <div v-for="book in books" :key="book.book_id">
      <el-row class="title">
        <span> {{ book.title }} </span>
      </el-row>

      <el-row class="content">
        <el-link :href="book.url" target="_blank">
          {{ book.provider_id }}.{{ book.book_id }}
        </el-link>
      </el-row>

      <el-row v-for="group in book.files" :key="group.lang" class="content">
        <el-col :span="4">
          <span>
            {{
              readableLang(group.lang) +
              '(' +
              group.cached_episode_number +
              '/' +
              group.total_episode_number +
              ')'
            }}
          </span>
        </el-col>
        <el-col :span="20">
          <el-space spacer="|">
            <el-link
              v-for="file in group.files"
              :href="filenameToUrl(file.filename)"
              :icon="Download"
              :disabled="file.filename === null"
            >
              {{ file.type.toUpperCase() }}
            </el-link>
            <el-link
              v-for="file in group.mixed_files"
              :href="filenameToUrl(file.filename)"
              :icon="Download"
              :disabled="file.filename === null"
            >
              原文对比版{{ file.type.toUpperCase() }}
            </el-link>
          </el-space>
        </el-col>
      </el-row>

      <el-divider />
    </div>
  </div>
  <el-pagination
    :current-page="currentPage"
    :total="total"
    layout="prev, pager, next, ->, jumper"
    @current-change="onPageChange"
  />
</template>

<style>
.list {
  width: 650px;
  min-height: 650px;
}
.title {
  font-size: 18px;
}
.content {
  font-size: 14px;
  margin-top: 10px;
  margin-left: 20px;
}
</style>
