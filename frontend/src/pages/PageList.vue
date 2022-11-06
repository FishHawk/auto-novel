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
</script>

<template>
  <div class="list">
    <li v-for="book in books" :key="book.book_id">
      <el-link :href="book.url" target="_blank" class="title">
        {{ book.title }}
      </el-link>

      <div v-for="group in book.files" :key="group.lang" class="content">
        <el-row>
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
                {{ file.type }}
              </el-link>
            </el-space>
          </el-col>
        </el-row>
      </div>

      <el-divider />
    </li>
  </div>
</template>

<style>
.list {
  margin-top: 15%;
  width: 650px;
}
.title {
  font-size: 18px;
}
.content {
  font-size: 14px;
  margin-top: 10px;
  margin-left: 30px;
}
</style>
