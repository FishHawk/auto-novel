<script lang="ts" setup>
import { ref, watch } from 'vue';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import { Page } from '@/data/api/page';
import {
  ApiOperationHistory,
  Operation,
  OperationHistory,
  OperationType,
} from '@/data/api/api_operation_history';

import OperationWenkuEdit from './components/OperationWenkuEdit.vue';
import OperationWenkuUpload from './components/OperationWenkuUpload.vue';

const type = ref<OperationType>('wenku-upload');
const typeOptions = [
  { value: 'wenku-upload', label: '文库上传' },
  { value: 'wenku-edit', label: '文库编辑' },
];

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const historiesResult = ref<ResultState<Page<OperationHistory<Operation>>>>();

async function loadPage(page: number) {
  historiesResult.value = undefined;
  const result = await ApiOperationHistory.listOperationHistory({
    page: currentPage.value - 1,
    pageSize: 30,
    type: type.value,
  });
  if (currentPage.value == page) {
    historiesResult.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

async function deleteHistory(id: string) {
  const result = await ApiOperationHistory.deleteOperationHistory(id);
  if (result.ok) {
    message.info('删除成功');
    if (historiesResult.value?.ok) {
      historiesResult.value.value.items =
        historiesResult.value.value.items.filter((it) => it.id !== id);
    }
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

watch(currentPage, (page) => loadPage(page), { immediate: true });

watch(type, () => {
  if (currentPage.value === 1) loadPage(1);
  else currentPage.value = 1;
});
</script>

<template>
  <MainLayout>
    <n-h1>操作历史</n-h1>

    <n-p>
      <n-radio-group v-model:value="type" name="operation-type">
        <n-space>
          <n-radio
            v-for="option in typeOptions"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </n-radio>
        </n-space>
      </n-radio-group>
    </n-p>

    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
    <n-divider />

    <n-list v-if="historiesResult?.ok">
      <n-list-item v-for="item in historiesResult.value.items">
        <operation-wenku-upload
          v-if="item.operation.type === 'wenku-upload'"
          :op="item.operation"
        />
        <operation-wenku-edit
          v-else-if="item.operation.type === 'wenku-edit'"
          :op="item.operation"
        />
        <n-space>
          <n-text>
            于<n-time :time="item.createAt * 1000" type="relative" /> 由{{
              item.operator.username
            }}执行
          </n-text>
          <n-button type="error" text @click="deleteHistory(item.id)">
            删除
          </n-button>
        </n-space>
      </n-list-item>
    </n-list>

    <n-divider />
    <n-pagination
      v-if="pageNumber > 1"
      v-model:page="currentPage"
      :page-count="pageNumber"
      :page-slot="7"
    />
  </MainLayout>
</template>
