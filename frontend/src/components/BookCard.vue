<script setup lang="ts">
import { Download } from '@element-plus/icons-vue';
import { ElMessage, FormInstance } from 'element-plus';
import { reactive, ref } from 'vue';
import {
  Book,
  readableLang,
  readableStatus,
  filenameToUrl,
  LocalBoostProgress,
} from '../models/Book';

defineProps<{
  book: Book;
  localBoostProgress: LocalBoostProgress | undefined;
}>();

const emit = defineEmits<{
  (
    e: 'onNormalUpdate',
    lang: string,
    start_index: number,
    end_index: number
  ): void;
  (e: 'onLocalBoost', start_index: number, end_index: number): void;
}>();

const dialogFormVisible = ref(false);
const formRef = ref<FormInstance>();
interface UpdateForm {
  start_index: number;
  end_index: number;
  lang: string;
  type: '常规更新' | '本地加速';
}
const form = reactive({
  start_index: 1,
  end_index: 65536,
  lang: '',
  type: '常规更新',
} as UpdateForm);

function openUpdateDialog(lang: string) {
  form.lang = lang;
  dialogFormVisible.value = true;
}

async function submitForm(formEl: FormInstance | undefined) {
  if (!formEl) return;
  await formEl.validate((valid, fields) => {
    if (valid) {
      const start_index = form.start_index ?? 1;
      const end_index = form.end_index ?? 65526;
      if (form.type === '常规更新') {
        emit('onNormalUpdate', form.lang, start_index - 1, end_index);
      } else {
        emit('onLocalBoost', start_index - 1, end_index);
      }
      dialogFormVisible.value = false;
    } else {
      ElMessage.error(`更新表单${fields}字段不合法。`);
    }
  });
}

function getPercentage(progress: LocalBoostProgress): number {
  if (progress.total === undefined) {
    return 0;
  } else {
    return (100 * (progress.finished + progress.error)) / progress.total;
  }
}
</script>

<template>
  <el-card
    v-if="book !== undefined"
    :body-style="{ padding: '0px' }"
    style="width: 720"
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

      <el-table-column label="操作" align="left" header-align="center">
        <template #default="scope">
          <el-button
            @click="$emit('onNormalUpdate', scope.row.lang, 1, 65536)"
            type="primary"
            size="small"
            color="#2c3e50"
          >
            更新
          </el-button>
          <el-button
            @click="$emit('onLocalBoost', 1, 65536)"
            v-if="scope.row.lang === 'zh'"
            type="primary"
            size="small"
            color="#2c3e50"
          >
            本地加速
          </el-button>
          <el-button
            @click="openUpdateDialog(scope.row.lang)"
            type="primary"
            size="small"
            color="#2c3e50"
          >
            高级
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-row
      v-if="localBoostProgress !== undefined"
      :gutter="10"
      align="middle"
      class="text"
    >
      <el-col :span="3">
        <span>本地加速</span>
      </el-col>
      <el-col :span="12">
        <el-progress
          :percentage="getPercentage(localBoostProgress)"
          :indeterminate="localBoostProgress.total === undefined"
          :show-text="false"
          color="#2c3e50"
        />
      </el-col>
      <el-col :span="9">
        <el-space spacer="|" style="justify-content: center; width: 100%">
          <span>成功:{{ localBoostProgress.finished ?? '-' }}</span>
          <span>失败:{{ localBoostProgress.error ?? '-' }}</span>
          <span>总共:{{ localBoostProgress.total ?? '-' }}</span>
        </el-space>
      </el-col>
    </el-row>
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
      <el-form-item label="到这章为止">
        <el-input-number
          v-model="form.end_index"
          :min="1"
          controls-position="right"
          size="large"
        />
      </el-form-item>
      <el-form-item label="更新方式">
        <el-radio-group v-model="form.type" v-if="form.lang === 'zh'">
          <el-radio label="常规更新" />
          <el-radio label="本地加速" />
        </el-radio-group>
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
.text {
  padding: 10px;
  text-align: 'center';
  font-size: 14px;
  color: #606266;
}
</style>
