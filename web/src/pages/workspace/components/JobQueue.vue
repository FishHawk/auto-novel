<script lang="ts" setup>
import { TranslateJob } from '@/data/stores/workspace';

defineProps<{
  job: TranslateJob;
  percentage?: number;
}>();
const emit = defineEmits<{
  topJob: [];
  deleteJob: [];
}>();
</script>

<template>
  <n-thing>
    <template #header>
      <n-text depth="3" style="font-size: 12px">
        {{ job.task }}
      </n-text>
    </template>
    <template #header-extra>
      <n-flex size="small" :wrap="false">
        <c-button label="置顶" size="tiny" secondary @click="emit('topJob')" />
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
