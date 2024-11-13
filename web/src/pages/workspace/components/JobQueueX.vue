<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  DragIndicatorOutlined,
  KeyboardDoubleArrowDownOutlined,
  KeyboardDoubleArrowUpOutlined,
  RefreshOutlined,
} from '@vicons/material';

import { WorkspaceJob } from './Workspace';

const props = defineProps<{
  job: WorkspaceJob;
}>();

const emit = defineEmits<{
  retryJob: [];
  topJob: [];
  bottomJob: [];
  deleteJob: [];
}>();

const countTotal = computed(() => {
  return props.job.tasks.length;
});
const countSuccess = computed(() => {
  return props.job.tasks.filter((it) => it.state === 'success').length;
});
const countFailed = computed(() => {
  return props.job.tasks.filter((it) => it.state === 'failed').length;
});
const progressText = computed(() => {
  let text = '';
  if (props.job.state === 'pending') {
    return undefined;
  } else if (props.job.state === 'processing') {
    text = '处理中';
  } else if (props.job.state === 'finished') {
    text = countSuccess < countTotal ? '未完成' : '已完成';
  } else {
    return props.job.state satisfies never;
  }
  return `${text} - 总共 ${countTotal.value} / 成功 ${countSuccess.value} / 失败 ${countFailed.value}`;
});
const progressPercentage = computed(() => {
  if (props.job.state !== 'processing') {
    return undefined;
  } else if (countTotal.value === 0) {
    return 100;
  } else {
    const countFinished = countSuccess.value + countFailed.value;
    return Math.round((1000 * countFinished) / countTotal.value) / 10;
  }
});
</script>

<template>
  <n-thing>
    <template #avatar>
      <n-flex vertical justify="center" style="height: 100%">
        <n-icon
          class="drag-trigger"
          :size="18"
          :depth="2"
          :component="DragIndicatorOutlined"
          style="cursor: move"
        />
      </n-flex>
    </template>

    <template #header>
      <job-task-link :task="job.descriptor" />
    </template>

    <template #header-extra>
      <n-flex :size="6" :wrap="false">
        <c-icon-button
          v-if="job.state === 'finished'"
          tooltip="重试"
          :icon="RefreshOutlined"
          @action="emit('retryJob')"
        />

        <c-icon-button
          tooltip="置顶"
          :icon="KeyboardDoubleArrowUpOutlined"
          @action="emit('topJob')"
        />

        <c-icon-button
          tooltip="置底"
          :icon="KeyboardDoubleArrowDownOutlined"
          @action="emit('bottomJob')"
        />

        <c-icon-button
          tooltip="删除"
          :icon="DeleteOutlineOutlined"
          type="error"
          @action="emit('deleteJob')"
        />
      </n-flex>
    </template>

    <template #description>
      <n-flex vertical>
        <n-text>
          {{ job.name }}
        </n-text>
        <n-text v-if="progressText" depth="3">
          {{ progressText }}
        </n-text>
        <n-progress
          v-if="progressPercentage"
          :percentage="progressPercentage"
          style="max-width: 600px"
        />
        <n-flex v-for="task in job.tasks" :wrap="false">
          {{ task.name }}
          <n-flex>
            <div
              v-for="seg in task.segs"
              class="workspace-seg"
              :data-state="seg.state"
            ></div>
          </n-flex>
        </n-flex>
      </n-flex>
    </template>
  </n-thing>
</template>

<style scoped>
.workspace-seg {
  width: 16px;
  height: 16px;
}
.workspace-seg div[data-state='pending'] {
  background-color: grey;
}
.workspace-seg div[data-state='processing'] {
  background-color: blue;
}
.workspace-seg div[data-state='success'] {
  background-color: green;
}
.workspace-seg div[data-state='fallback-success'] {
  background-color: yellow;
}
.workspace-seg div[data-state='failed'] {
  background-color: red;
}
</style>
