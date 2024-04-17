<script lang="ts" setup>
import { DeleteOutlineOutlined, RefreshOutlined } from '@vicons/material';

import {
  TranslateJob,
  TranslateJobRecord,
  TranslateTaskDescriptor,
} from '@/model/Translator';

const props = defineProps<{
  job: TranslateJobRecord;
}>();
const emit = defineEmits<{
  retryJob: [];
  deleteJob: [];
}>();
const isFinished = computed(() => TranslateJob.isFinished(props.job));

const url = computed(() => TranslateTaskDescriptor.parseUrl(props.job.task));
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
          v-if="!isFinished"
          tooltip="重试"
          :icon="RefreshOutlined"
          @action="emit('retryJob')"
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
      <br />
      <n-text depth="3">
        <template v-if="!isFinished">
          未完成
          <template v-if="job.progress !== undefined">
            总共 {{ job.progress?.total }} / 成功 {{ job.progress?.finished }} /
            失败 {{ job.progress?.error }}
          </template>
        </template>
        <template v-else>已完成</template>
      </n-text>
    </template>
  </n-thing>
</template>
