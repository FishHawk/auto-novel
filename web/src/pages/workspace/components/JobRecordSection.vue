<script lang="ts" setup>
import { DeleteOutlineOutlined, RefreshOutlined } from '@vicons/material';

import {
  WorkspaceStore,
  isTranslateJobFinished,
} from '@/data/stores/workspace';

const props = defineProps<{
  workspace: WorkspaceStore;
}>();

const progressFilter = ref<'all' | 'finished' | 'unfinished'>('all');
const progressFilterOptions = [
  { value: 'all', label: '全部' },
  { value: 'finished', label: '已完成' },
  { value: 'unfinished', label: '未完成' },
];

const records = computed(() => {
  const recordsAll = props.workspace.uncompletedJobs;
  if (progressFilter.value === 'finished') {
    return recordsAll.filter((it) => isTranslateJobFinished(it));
  } else if (progressFilter.value === 'unfinished') {
    return recordsAll.filter((it) => !isTranslateJobFinished(it));
  } else {
    return recordsAll;
  }
});
</script>

<template>
  <section-header title="任务记录"> </section-header>

  <n-flex vertical>
    <c-action-wrapper title="状态">
      <c-radio-group
        v-model:value="progressFilter"
        :options="progressFilterOptions"
        size="small"
      />
    </c-action-wrapper>
    <c-action-wrapper title="操作">
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
