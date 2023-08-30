<script setup lang="ts" generic="T extends any">
import { ResultState } from '@/data/api/result';

defineProps<{
  result: ResultState<T>;
  showEmpty: (value: T) => boolean;
}>();
</script>

<template>
  <template v-if="result?.ok">
    <slot :value="result.value" />
    <n-empty v-if="showEmpty(result.value)" description="空列表" />
  </template>

  <n-result
    v-else-if="result && !result.ok"
    status="error"
    title="加载错误"
    :description="result.error.message"
  />
</template>
