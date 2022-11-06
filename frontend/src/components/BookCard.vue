<script setup lang="ts">
import { Download } from '@element-plus/icons-vue';
import {
  Book,
  BookFileGroup,
  readableLang,
  readableStatus,
  filenameToUrl,
} from '../models/Book';

defineProps<{ book: Book }>();
defineEmits<{ (e: 'onUpdate', lang: string): void }>();

function isUpdateEnabled(group: BookFileGroup): boolean {
  const isNotComplete =
    group.status == null &&
    group.total_episode_number > group.cached_episode_number;
  const hasMissingFile = group.files.some((it) => it.filename === null);
  return isNotComplete || hasMissingFile;
}
</script>

<template>
  <el-card
    v-if="book !== undefined"
    :body-style="{ padding: '0px' }"
    style="width: 600px"
    target="_blank"
  >
    <template #header>
      <el-link :href="book.url">
        {{ book.title }}
      </el-link>
    </template>

    <el-table :data="book.files" table-layout="auto" stripe>
      <el-table-column label="语言" align="center">
        <template #default="scope">
          <span>{{ readableLang(scope.row.lang) }}</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" align="center">
        <template #default="scope">
          <span>
            {{
              readableStatus(
                scope.row.status,
                scope.row.total_episode_number,
                scope.row.cached_episode_number
              )
            }}
          </span>
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
            >
              {{ file.type }}
            </el-link>
          </el-space>
        </template>
      </el-table-column>

      <el-table-column label="操作" align="center">
        <template #default="scope">
          <el-button
            @click="$emit('onUpdate', scope.row.lang)"
            :disabled="!isUpdateEnabled(scope.row)"
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
