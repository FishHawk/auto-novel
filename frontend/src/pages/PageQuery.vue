<script lang="ts" setup>
import { onMounted, Ref, ref } from 'vue';
import { ElMessage } from 'element-plus';
import 'element-plus/es/components/message/style/css';
import axios from 'axios';

import SearchBar from '../components/SearchBar.vue';
import { Book, SearchHistory } from '../models/Book';
import { handleError } from '../models/Util';

const bookRef: Ref<Book | undefined> = ref();
const loadingRef = ref(false);
let query_id = 0;
let poll_id: number | undefined = undefined;

const historiesRef: Ref<SearchHistory[]> = ref([]);

onMounted(() => {
  const histories_raw = localStorage.getItem('histories');
  if (histories_raw) {
    try {
      historiesRef.value = JSON.parse(histories_raw);
    } catch (e) {
      localStorage.removeItem('histories');
    }
  }
});

function addHistory(history: SearchHistory) {
  const histories = historiesRef.value.filter(
    (item) => item.url != history.url
  );
  histories.unshift(history);

  const histories_length_limit = 10;
  if (histories.length > histories_length_limit)
    histories.length = histories_length_limit;

  historiesRef.value = histories;
  const parsed = JSON.stringify(historiesRef.value);
  localStorage.setItem('histories', parsed);
}

function onSearch(url: string) {
  if (loadingRef.value) return;

  bookRef.value = undefined;
  loadingRef.value = true;
  query_id += 1;
  clearTimeout(poll_id);

  const query_id_snapshot = query_id;
  axios
    .get('api/query', { params: { url } })
    .then((res) => {
      if (query_id_snapshot == query_id) {
        const book = res.data as Book;
        bookRef.value = book;
        loadingRef.value = false;
        addHistory({ url: book.url, title: book.title });
        poll_id = window.setTimeout(() => {
          pollSearch(url, query_id_snapshot);
        }, 1000);
      }
    })
    .catch((error) => {
      if (query_id_snapshot == query_id) {
        loadingRef.value = false;
        handleError(error, '查询失败');
      }
    });
}

function pollSearch(url: string, query_id_snapshot: number) {
  return axios
    .get('api/query', {
      params: { url },
    })
    .then((res) => {
      if (query_id_snapshot == query_id) {
        bookRef.value = res.data;
      }
    })
    .finally(() => {
      if (query_id_snapshot == query_id) {
        poll_id = window.setTimeout(() => {
          pollSearch(url, query_id_snapshot);
        }, 1000);
      }
    });
}

function onUpdate(lang: string, start_index?: number) {
  if (bookRef.value === undefined) return;
  axios
    .post('api/book-update', {
      provider_id: bookRef.value.provider_id,
      book_id: bookRef.value.book_id,
      lang,
      start_index,
    })
    .then((_) => {
      ElMessage.success(`更新任务已经进入队列。`);
    })
    .catch((error) => {
      handleError(error, '更新失败');
    });
}
</script>

<template>
  <el-col style="margin-top: 15%">
    <h1>网络小说 EPUB/TXT 生成器</h1>

    <el-row justify="center">
      <SearchBar
        @onSearch="onSearch"
        :loading="loadingRef"
        :histories="historiesRef"
      />
    </el-row>

    <ul>
      <li class="support-text">
        链接示例(KAKUYOMU): https://kakuyomu.jp/works/16817139555217983105
      </li>
      <li class="support-text">
        链接示例(成为小说家吧): https://ncode.syosetu.com/n0833hi
      </li>
      <li class="support-text">
        如果中文版本更新完仍不完整，说明翻译额度已被耗尽，请等待几小时再尝试。
      </li>
      <li class="support-text">
        你也可以直接浏览缓存的
        <a href="#/list" target="_blank">文件列表 </a>
        。
      </li>
    </ul>

    <el-row justify="center" style="margin-top: 40px">
      <BookCard
        v-if="bookRef !== undefined"
        :book="bookRef"
        @onUpdate="onUpdate"
      />
    </el-row>

    <el-link
      href="https://github.com/FishHawk/web-novel-ebook-generator"
      target="_blank"
      class="footer"
    >
      @GitHub
    </el-link>
  </el-col>
</template>

<style scoped>
.support-text {
  color: #b4bcc2;
  font-size: 20px;
  margin-top: 5px;
  margin-bottom: 5px;
}

.support-text a {
  color: #b4bcc2;
}

.footer {
  position: fixed;
  left: 50%;
  bottom: 20px;
  transform: translate(-50%, -50%);
  margin: 0 auto;
  color: #b4bcc2;
}
</style>
