<script setup lang="ts" generic="T extends any">
import { Result } from '@/data/result';

defineProps<{
  result?: Result<T>;
  showEmpty: (value: T) => boolean;
}>();
</script>

<template>
  <template v-if="result?.ok">
    <n-empty v-if="showEmpty(result.value)" description="空列表" />
    <slot v-else :value="result.value" />
  </template>

  <n-result
    v-else-if="result && !result.ok"
    status="error"
    title="加载错误"
    :description="result.error.message"
  />
</template>
