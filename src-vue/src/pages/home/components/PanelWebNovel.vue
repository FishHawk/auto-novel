<script lang="ts" setup>
import { ResultState } from '@/data/api/result';
import { WebNovelListItemDto } from '@/data/api/api_web_novel';

defineProps<{
  list: ResultState<WebNovelListItemDto[]>;
}>();
</script>

<template>
  <div v-if="list?.ok">
    <n-grid :x-gap="12" :y-gap="12" cols="1 600:4">
      <n-grid-item v-for="item in list.value" style="padding: 8px">
        <n-a
          :href="`/novel/${item.providerId}/${item.novelId}`"
          target="_blank"
          class="text-2line"
          >{{ item.titleJp }}</n-a
        >
        <div class="text-2line">{{ item.titleZh }}</div>
        <div style="color: #666">
          总计{{ item.total }} / 百度{{ item.countBaidu }} / 有道{{
            item.countYoudao
          }}
        </div>
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