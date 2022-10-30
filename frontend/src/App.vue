<template>
  <el-col>
    <h1 style="margin-top: 15%">网络小说 epub/txt 生成器</h1>
    <el-row justify="center" style="border: 2px solid #2c3e50">
      <el-input
        ref="input"
        class="inputDeep"
        v-model="book_url"
        size="large"
        placeholder="请输入小说链接..."
        autofocus="true"
        style="width: 600px; height: 60px"
      />
      <!-- @search="onSearch" -->
      <el-button
        @click="onSearch"
        type="primary"
        color="#2c3e50"
        :loading="loading.valueOf()"
        style="width: 120px; height: 60px; font-size: 20px"
      >
        查询
      </el-button>
    </el-row>

    <div style="margin-top: 20px">
      <span style="color: #b4bcc2; font-size: 20px; margin: 60px">
        支持KAKUYOMU(カクヨム)和成为小说家吧(小説家になろう)。
      </span>
    </div>

    <div style="margin-top: 20px">
      <span style="color: #b4bcc2; font-size: 20px; margin: 60px">
        中文翻译可能会因为额度限制失败，失败后冷却两天。
      </span>
    </div>

    <div style="margin-top: 20px">
      <span style="color: #b4bcc2; font-size: 20px; margin: 60px">
        链接示例: https://kakuyomu.jp/works/16817139555217983105
      </span>
    </div>

    <el-row justify="center" style="margin-top: 40px">
      <el-card
        v-if="book !== undefined"
        :body-style="{ padding: '0px' }"
        style="width: 450px"
      >
        <template #header>
          <el-col>
            <span>{{ book.title }}</span>
          </el-col>
        </template>

        <el-table :data="book.files" table-layout="auto" stripe>
          <el-table-column label="语言" align="center">
            <template #default="scope">
              <span>{{ readableLang(scope.row.lang) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="状态" align="center">
            <template #default="scope">
              <span>{{
                readableStatus(
                  scope.row.status,
                  scope.row.total_episode_number,
                  scope.row.cached_episode_number
                )
              }}</span>
            </template>
          </el-table-column>

          <el-table-column label="链接" align="center">
            <template #default="scope">
              <el-space spacer="|">
                <el-link
                  v-for="file in scope.row.files"
                  :href="filenameToUrl(file.filename)"
                  :icon="Download"
                  :disabled="file.filename === null"
                  >{{ file.type.toUpperCase() }}</el-link
                >
              </el-space>
            </template>
          </el-table-column>

          <el-table-column label="操作" align="center">
            <template #default="scope">
              <el-button
                @click="onUpdate(scope.row.lang)"
                :disabled="
                  !(
                    scope.row.status == null &&
                    scope.row.total_episode_number >
                      scope.row.cached_episode_number
                  )
                "
                type="primary"
                color="#2c3e50"
              >
                更新
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </el-row>
  </el-col>
</template>

<script lang="ts" setup>
import { Ref, ref } from 'vue';
import { ElMessage } from 'element-plus';
import 'element-plus/es/components/message/style/css';
import { Download } from '@element-plus/icons-vue';
import axios from 'axios';

type BookStatus = 'queued' | 'started' | 'failed' | 'unknown' | null;

interface BookFile {
  type: string;
  filename: string;
}

interface BookFileGroup {
  lang: string;
  type: string;
  status: BookStatus;
  total_episode_number: number;
  cached_episode_number: number;
  files: BookFile[];
}

interface Book {
  provider_id: string;
  book_id: string;
  title: string;
  files: BookFileGroup[];
}

const loading = ref(false);
const book_url = ref('');
const book: Ref<Book | undefined> = ref();

function onSearch() {
  if (loading.value == true) return;
  loading.value = true;
  book.value = undefined;
  const url = book_url.value;
  axios
    .get(window.location.href + 'api/query', {
      params: { url },
    })
    .then((json) => {
      loading.value = false;
      json.data.url = url;
      book.value = json.data;
    })
    .catch((error) => {
      loading.value = false;
      ElMessage.error(`查询失败：${error}`);
    });
}

function onUpdate(lang: string) {
  if (book.value === undefined) return;
  axios
    .post(window.location.href + 'api/book-update', {
      provider_id: book.value.provider_id,
      book_id: book.value.book_id,
      lang,
    })
    .then((text) => {
      ElMessage.success(`更新成功，任务已经进入队列。`);
    })
    .catch((error) => {
      ElMessage.error(`更新失败：${error}`);
    });
}

function readableLang(lang: string): string {
  if (lang == 'zh') return '中文';
  else if (lang == 'jp') return '日文';
  else return `未知(${lang})`;
}

function readableStatus(
  status: BookStatus,
  total: number,
  cached: number
): string {
  const page_status = `(${cached}/${total})`;
  if (status == 'queued') return '排队中' + page_status;
  else if (status == 'started') return '更新中' + page_status;
  else if (status == 'failed') return '失败' + page_status;
  else if (status == 'unknown') return '未知' + page_status;
  else {
    if (total > cached) return `不完整(${cached}/${total})`;
    else return `完整(${cached}/${total})`;
  }
}

function filenameToUrl(filename: string): string {
  return window.location.href + 'books/' + filename;
}
</script>

<style scoped>
.el-input {
  --el-color-primary: #2c3e50;
  --el-input-border-color: transparent;
  --el-input-hover-border-color: transparent;
  --el-input-clear-hover-color: transparent;
  --el-input-focus-border-color: transparent;
  font-size: 20px;
}
.el-table {
  --el-color-primary: #2c3e50;
}
.el-button {
  border-radius: 0px;
}
</style>
