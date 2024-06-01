<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  RefreshOutlined,
  ArrowDownwardFilled,
} from '@vicons/material';

import { Locator } from '@/data';
import { TranslateJob, TranslateTaskDescriptor } from '@/model/Translator';

import { useBookshelfLocalStore } from '@/pages/bookshelf/BookshelfLocalStore';
import { querySearch } from '@/util';

const props = defineProps<{
  id: 'gpt' | 'sakura';
}>();

const workspace =
  props.id === 'gpt'
    ? Locator.gptWorkspaceRepository()
    : Locator.sakuraWorkspaceRepository();
const workspaceRef = workspace.ref;
const message = useMessage();
const store = useBookshelfLocalStore();

const progressFilter = ref<'all' | 'finished' | 'unfinished'>('all');
const progressFilterOptions = [
  { value: 'all', label: '全部' },
  { value: 'finished', label: '已完成' },
  { value: 'unfinished', label: '未完成' },
];

const search = reactive({
  query: '',
  enableRegexMode: false,
});

const records = computed(() => {
  let recordsAll = workspaceRef.value.uncompletedJobs;

  recordsAll = querySearch(recordsAll, 'description', search);
  
  if (progressFilter.value === 'finished') {
    return recordsAll.filter((it) => TranslateJob.isFinished(it));
  } else if (progressFilter.value === 'unfinished') {
    return recordsAll.filter((it) => !TranslateJob.isFinished(it));
  } else {
    return recordsAll;
  }
});

const downloadVolumes = async () => {
  const volumeIds: string[] = [];
  const recordsAll = workspaceRef.value.uncompletedJobs;

  recordsAll.forEach((it) => {
    if (!TranslateJob.isFinished(it)) {
      return;
    }
    const { desc } = TranslateTaskDescriptor.parse(it.task);
    if (desc.type === 'local') {
      volumeIds.push(desc.volumeId);
    }
  });

  if (volumeIds.length === 0) {
    message.info('列表为空，没有文件需要下载');
    return;
  }
  const { success, failed } = await store.downloadVolumes(volumeIds);
  message.info(`${success}本小说被打包，${failed}本失败`);
};
</script>

<template>
  <section-header title="任务记录"> </section-header>

  <n-flex vertical>
    <c-action-wrapper title="搜索">
      <search-input
        v-model:value="search"
        placeholder="搜索文件名"
        style="max-width: 400px"
      />
    </c-action-wrapper>
    <c-action-wrapper title="状态">
      <c-radio-group
        v-model:value="progressFilter"
        :options="progressFilterOptions"
        size="small"
      />
    </c-action-wrapper>
    <c-action-wrapper title="操作" align="center">
      <n-button-group size="small">
        <c-button
          label="重试未完成任务"
          :icon="RefreshOutlined"
          :round="false"
          @action="workspace.retryAllJobRecords()"
        />
        <c-button
          label="删除所有记录"
          :icon="DeleteOutlineOutlined"
          :round="false"
          @action="workspace.deleteAllJobRecords()"
        />
        <c-button
          label="批量下载已完成记录"
          :icon="ArrowDownwardFilled"
          :round="false"
          @action="downloadVolumes"
        />
      </n-button-group>
    </c-action-wrapper>
  </n-flex>

  <n-divider style="margin: 16px 0 0" />

  <n-empty
    v-if="records.length === 0"
    description="没有任务"
    style="padding: 32px"
  />
  <n-list>
    <n-list-item v-for="job of records" :key="job.task">
      <job-record
        :job="job"
        @retry-job="workspace.retryJobRecord(job)"
        @delete-job="workspace.deleteJobRecord(job)"
      />
    </n-list-item>
  </n-list>
</template>
