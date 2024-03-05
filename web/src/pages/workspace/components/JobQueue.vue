<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  KeyboardDoubleArrowDownOutlined,
  KeyboardDoubleArrowUpOutlined,
} from '@vicons/material';
import { computed } from 'vue';

import { TranslateJob } from '@/data/stores/workspace';

import { parseTaskUrl } from './util';

const props = defineProps<{
  job: TranslateJob;
  percentage?: number;
}>();
const emit = defineEmits<{
  topJob: [];
  bottomJob: [];
  deleteJob: [];
}>();

const url = computed(() => parseTaskUrl(props.job.task));
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
          @click="emit('topJob')"
        />

        <c-icon-button
          tooltip="置底"
          :icon="KeyboardDoubleArrowDownOutlined"
          @click="emit('bottomJob')"
        />

        <c-icon-button
          tooltip="删除"
          :icon="DeleteOutlineOutlined"
          type="error"
          @click="emit('deleteJob')"
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
