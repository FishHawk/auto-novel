<script lang="ts" setup>
import {
  VerticalAlignBottomOutlined,
  VerticalAlignTopOutlined,
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
      <n-flex size="small" :wrap="false">
        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button text @click="emit('topJob')">
              <n-icon :component="VerticalAlignTopOutlined" />
            </n-button>
          </template>
          置顶
        </n-tooltip>

        <n-tooltip trigger="hover">
          <template #trigger>
            <n-button text @click="emit('bottomJob')">
              <n-icon :component="VerticalAlignBottomOutlined" />
            </n-button>
          </template>
          置底
        </n-tooltip>

        <c-button
          label="删除"
          size="tiny"
          secondary
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
