<script lang="ts" setup>
import { WebNovelOutlineDto } from '@/data/api/api_web_novel';
import { Result } from '@/data/result';

defineProps<{ listResult?: Result<WebNovelOutlineDto[]> }>();
</script>

<template>
  <ResultView
    :result="listResult"
    :showEmpty="(it: WebNovelOutlineDto[]) => it.length === 0"
    v-slot="{ value: list }"
  >
    <n-grid :x-gap="12" :y-gap="12" cols="1 850:4">
      <n-grid-item v-for="item in list" style="padding: 8px">
        <RouterNA :to="`/novel/${item.providerId}/${item.novelId}`">
          <span class="text-2line">
            {{ item.titleJp }}
          </span>
        </RouterNA>
        <div class="text-2line">{{ item.titleZh }}</div>
        <n-text depth="3">
          总计 {{ item.total }} / 百度 {{ item.baidu }} / 有道 {{ item.youdao }}
        </n-text>
      </n-grid-item>
    </n-grid>
  </ResultView>
</template>
