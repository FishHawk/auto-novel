<script lang="ts" setup>
import { ResultState } from '@/data/api/result';
import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';

defineProps<{ listResult: ResultState<WenkuNovelOutlineDto[]> }>();
</script>

<template>
  <ResultView
    :result="listResult"
    :showEmpty="(it: WenkuNovelOutlineDto[]) => it.length === 0"
    v-slot="{ value: list }"
  >
    <n-grid :x-gap="12" :y-gap="12" cols="3 600:6">
      <n-grid-item v-for="item in list">
        <RouterNA :to="`/wenku/${item.id}`">
          <ImageCard
            :src="item.cover"
            :title="item.titleZh ? item.titleZh : item.title"
          />
        </RouterNA>
      </n-grid-item>
    </n-grid>
  </ResultView>
</template>
