<script lang="ts" setup>
import { OperationRepository } from '@/data/api';
import { Result, runCatching } from '@/util/result';
import { OperationHistory, OperationType } from '@/model/Operation';
import { Page } from '@/model/Page';
import { doAction } from '@/pages/util';

import OperationWenkuEdit from './components/OperationWenkuEdit.vue';
import OperationWenkuUpload from './components/OperationWenkuUpload.vue';

const type = ref<OperationType>('web-edit');
const typeOptions = [
  { value: 'web-edit', label: '网络编辑' },
  { value: 'web-edit-glossary', label: '网络编辑术语表' },
  { value: 'wenku-edit', label: '文库编辑' },
  { value: 'wenku-edit-glossary', label: '文库编辑术语表' },
  { value: 'wenku-upload', label: '文库上传' },
];

const message = useMessage();

const currentPage = ref(1);
const pageNumber = ref(1);
const historiesResult = ref<Result<Page<OperationHistory>>>();

async function loadPage(page: number) {
  historiesResult.value = undefined;
  const result = await runCatching(
    OperationRepository.listOperationHistory({
      page: currentPage.value - 1,
      pageSize: 30,
      type: type.value,
    }),
  );
  if (currentPage.value == page) {
    historiesResult.value = result;
    if (result.ok) {
      pageNumber.value = result.value.pageNumber;
    }
  }
}

const deleteHistory = (id: string) =>
  doAction(
    OperationRepository.deleteOperationHistory(id).then(() => {
      if (historiesResult.value?.ok) {
        historiesResult.value.value.items =
          historiesResult.value.value.items.filter((it) => it.id !== id);
      }
    }),
    '删除',
    message,
  );

watch(currentPage, (page) => loadPage(page), { immediate: true });

watch(type, () => {
  if (currentPage.value === 1) loadPage(1);
  else currentPage.value = 1;
});
</script>

<template>
  <n-p>
    <c-radio-group v-model:value="type" :options="typeOptions" />
  </n-p>

  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
  <n-divider />

  <c-result
    :result="historiesResult"
    :show-empty="(it: Page<any>) => it.items.length === 0"
    v-slot="{ value }"
  >
    <n-list>
      <n-list-item v-for="item in value.items">
        <operation-web-edit
          v-if="item.operation.type === 'web-edit'"
          :op="item.operation"
        />
        <operation-web-edit-glossary
          v-else-if="item.operation.type === 'web-edit-glossary'"
          :op="item.operation"
        />
        <operation-wenku-edit
          v-else-if="item.operation.type === 'wenku-edit'"
          :op="item.operation"
        />
        <operation-wenku-edit-glossary
          v-else-if="item.operation.type === 'wenku-edit-glossary'"
          :op="item.operation"
        />
        <operation-wenku-upload
          v-else-if="item.operation.type === 'wenku-upload'"
          :op="item.operation"
        />
        <n-flex>
          <n-text>
            于<n-time :time="item.createAt * 1000" type="relative" /> 由{{
              item.operator.username
            }}执行
          </n-text>
          <n-button type="error" text @click="deleteHistory(item.id)">
            删除
          </n-button>
        </n-flex>
      </n-list-item>
    </n-list>
  </c-result>

  <n-divider />
  <n-pagination
    v-if="pageNumber > 1"
    v-model:page="currentPage"
    :page-count="pageNumber"
    :page-slot="7"
  />
</template>
