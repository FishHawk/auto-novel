<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  KeyboardDoubleArrowDownOutlined,
  KeyboardDoubleArrowUpOutlined,
} from '@vicons/material';

import { TranslateJob } from '@/data/stores/workspace';

import { parseTaskUrl } from './util';

const props = defineProps<{
  job: TranslateJob;
  progress?: { finished: number; error: number; total: number };
}>();
const emit = defineEmits<{
  topJob: [];
  bottomJob: [];
  deleteJob: [];
}>();

const url = computed(() => parseTaskUrl(props.job.task));

const percentage = computed(() => {
  if (props.progress === undefined) {
    return 0;
  }
  const { finished, error, total } = props.progress;
  if (total === 0) {
    return 100;
  } else {
    return Math.round((1000 * (finished + error)) / total) / 10;
  }
});
</script>

<template>
  <n-thing>
    <template #header>
      <router-link v-if="url" :to="url">
        <n-text depth="3" underline style="font-size: 12px">
          {{ job.task }}
        </n-text>
      </router-link>
      <n-text v-else depth="3" style="font-size: 12px">
        {{ job.task }}
      </n-text>
    </template>
    <template #header-extra>
      <n-flex :size="6" :wrap="false">
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
      {{ job.description }}
      <template v-if="percentage">
        <n-progress :percentage="percentage" style="max-width: 600px" />
      </template>
    </template>
  </n-thing>
</template>
