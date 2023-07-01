<script lang="ts" setup>
import { ResultState } from '@/data/api/result';
import { WebNovelOutlineDto } from '@/data/api/api_web_novel';

defineProps<{ listResult: ResultState<WebNovelOutlineDto[]> }>();
</script>

<template>
  <ResultView :result="listResult" v-slot="{ value: list }">
    <n-grid :x-gap="12" :y-gap="12" cols="1 600:4">
      <n-grid-item v-for="item in list" style="padding: 8px">
        <n-a
          :href="`/novel/${item.providerId}/${item.novelId}`"
          target="_blank"
          class="text-2line"
          >{{ item.titleJp }}</n-a
        >
        <div class="text-2line">{{ item.titleZh }}</div>
        <div style="color: #666">
          总计{{ item.total }} / 百度{{ item.baidu }} / 有道{{ item.youdao }}
        </div>
      </n-grid-item>
    </n-grid>
  </ResultView>
</template>
