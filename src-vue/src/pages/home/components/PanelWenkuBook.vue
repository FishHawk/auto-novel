<script lang="ts" setup>
import { ResultState } from '@/data/api/result';
import { WenkuListItemDto } from '@/data/api/api_wenku_novel';

defineProps<{
  list: ResultState<WenkuListItemDto[]>;
}>();
</script>

<template>
  <div v-if="list?.ok">
    <n-grid :x-gap="12" :y-gap="12" cols="3 600:6">
      <n-grid-item v-for="item in list.value">
        <n-a :href="`/wenku/${item.bookId}`" target="_blank">
          <ImageCard :src="item.cover" :title="item.title" />
        </n-a>
      </n-grid-item>
    </n-grid>
    <n-empty v-if="list.value.length === 0" description="空列表" />
  </div>
  <n-result
    v-if="list && !list.ok"
    status="error"
    title="加载错误"
    :description="list.error.message"
  />
</template>

<style scoped>
.text-2line {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
