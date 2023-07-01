<script lang="ts" setup>
import { ResultState } from '@/data/api/result';
import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';

defineProps<{ listResult: ResultState<WenkuNovelOutlineDto[]> }>();
</script>

<template>
  <ResultView :result="listResult" v-slot="{ value: list }">
    <n-grid :x-gap="12" :y-gap="12" cols="3 600:6">
      <n-grid-item v-for="item in list">
        <n-a :href="`/wenku/${item.id}`" target="_blank">
          <ImageCard
            :src="item.cover"
            :title="item.titleZh ? item.titleZh : item.title"
          />
        </n-a>
      </n-grid-item>
    </n-grid>
  </ResultView>
</template>

<style scoped>
.text-2line {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
</style>
