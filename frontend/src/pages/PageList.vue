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
      console.log(res.data);
    })
    .catch((error) => {
      handleError(error, '查询失败');
    });
}
loadPage();

const activeNames = ref([]);
</script>

<template>
  <div class="list">
    <li v-for="book in books" :key="book.book_id">
      <el-link :href="book.url" target="_blank" class="title">
        {{ book.title }}
      </el-link>

      <div v-for="group in book.files" :key="group.lang" class="content">
        <span style="margin-left: 16px; margin-right: 16px">
          {{
            readableLang(group.lang) +
            '(' +
            group.cached_episode_number +
            '/' +
            group.total_episode_number +
            ')'
          }}
        </span>
        <el-space spacer="|">
          <el-link
            v-for="file in group.files"
            :href="filenameToUrl(file.filename)"
            :icon="Download"
            :disabled="file.filename === null"
          >
            {{ file.type }}
          </el-link>
        </el-space>
      </div>

      <el-divider />
    </li>
  </div>
</template>

<style>
.list {
  margin-top: 15%;
  width: 600px;
}
.title {
  font-size: 18px;
}
.content {
  font-size: 14px;
  margin-top: 10px;
}
</style>
