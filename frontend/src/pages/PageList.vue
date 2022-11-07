<script lang="ts" setup>
import { Ref, ref } from 'vue';
import { Download } from '@element-plus/icons-vue';
import { Book, readableLang, filenameToUrl } from '../models/Book';
import axios from 'axios';
import { handleError } from '../models/Util';

const books: Ref<Book[]> = ref([]);
function loadPage() {
  axios
    .get('api/list')
    .then((res) => {
      books.value = res.data;
    })
    .catch((error) => {
      handleError(error, '查询失败');
    });
}
loadPage();
</script>

<template>
  <div class="list">
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
</template>

<style>
.list {
  width: 650px;
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
