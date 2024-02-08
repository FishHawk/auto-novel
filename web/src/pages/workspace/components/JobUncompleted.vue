<script lang="ts" setup>
defineProps<{
  job: {
    task: string;
    description: string;
    progress?: { total: number; finished: number; error: number };
  };
}>();
const emit = defineEmits<{
  retryJob: [];
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
        <c-button
          label="重试"
          size="tiny"
          secondary
          @click="emit('retryJob')"
        />
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
      <template v-if="job.progress">
        总共 {{ job.progress?.total }} / 成功 {{ job.progress?.finished }} /
        失败 {{ job.progress?.error }}
      </template>
    </template>
  </n-thing>
</template>
