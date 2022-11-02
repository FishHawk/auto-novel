<script setup lang="ts">
import { Download } from '@element-plus/icons-vue';
import { Book, BookStatus } from '../models/Book';

defineProps<{ book: Book }>();
defineEmits<{ (e: 'onUpdate', lang: string): void }>();

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

<template>
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
            @click="$emit('onUpdate', scope.row.lang)"
            :disabled="
              !(
                scope.row.status == null &&
                scope.row.total_episode_number > scope.row.cached_episode_number
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
</template>

<style scoped>
.el-table {
  --el-color-primary: #2c3e50;
}
</style>
