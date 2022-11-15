<script setup lang="ts">
import { Download } from '@element-plus/icons-vue';
import { ElMessage, FormInstance } from 'element-plus';
import { reactive, ref } from 'vue';
import {
  Book,
  BookFileGroup,
  readableLang,
  readableStatus,
  filenameToUrl,
} from '../models/Book';

defineProps<{ book: Book }>();
const emit = defineEmits<{
  (e: 'onUpdate', lang: string, start_index?: number): void;
}>();

function isUpdateEnabled(group: BookFileGroup): boolean {
  const isNotComplete =
    group.status == null &&
    group.total_episode_number > group.cached_episode_number;
  const hasMissingFile =
    group.files.some((it) => it.filename === null) ||
    group.mixed_files.some((it) => it.filename === null);
  return isNotComplete || hasMissingFile;
}

const dialogFormVisible = ref(false);
const formRef = ref<FormInstance>();
const form = reactive({
  start_index: 1,
  lang: '',
});

function openDialog(lang: string) {
  form.lang = lang;
  dialogFormVisible.value = true;
}

async function submitForm(formEl: FormInstance | undefined) {
  if (!formEl) return;
  await formEl.validate((valid, fields) => {
    if (valid) {
      if (form.start_index == null || form.start_index == 1) {
        emit('onUpdate', form.lang);
      } else {
        emit('onUpdate', form.lang, form.start_index - 1);
      }
      dialogFormVisible.value = false;
    } else {
      ElMessage.error(`更新表单${fields}字段不合法。`);
    }
  });
}
</script>

<template>
  <el-card
    v-if="book !== undefined"
    :body-style="{ padding: '0px' }"
    style="width: 650px"
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
              {{ file.type.toUpperCase() }}
            </el-link>
          </el-space>
        </template>
      </el-table-column>

      <el-table-column label="原文对比版链接" align="center">
        <template #default="scope">
          <el-space spacer="|">
            <el-link
              v-for="file in scope.row.mixed_files"
              :href="filenameToUrl(file.filename)"
              :icon="Download"
              :disabled="file.filename === null"
            >
              {{ file.type.toUpperCase() }}
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
          <el-button
            @click="openDialog(scope.row.lang)"
            :disabled="!isUpdateEnabled(scope.row)"
            type="primary"
            color="#2c3e50"
          >
            高级
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>

  <el-dialog v-model="dialogFormVisible" title="更新" style="max-width: 400px">
    <el-form ref="formRef" :model="form">
      <el-form-item label="从这章开始更新">
        <el-input-number
          v-model="form.start_index"
          :min="1"
          controls-position="right"
          size="large"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="submitForm(formRef)">
          更新
        </el-button>
        <el-button @click="dialogFormVisible = false">取消</el-button>
      </el-form-item>
    </el-form>
  </el-dialog>
</template>

<style scoped>
.el-table {
  --el-color-primary: #2c3e50;
}
</style>
