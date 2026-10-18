<script lang="ts" setup>
import { XAsyncContent } from '@novelia/web-kit';

import type { WebNovelOutlineDto } from '@/model/WebNovel';

defineProps<{
  novels: WebNovelOutlineDto[] | undefined;
  error: Error | null;
}>();
</script>

<template>
  <template v-if="novels !== undefined">
    <n-grid :x-gap="12" :y-gap="12" cols="1 850:4">
      <n-grid-item
        v-for="item in novels"
        :key="`${item.providerId}/${item.novelId}`"
        style="padding: 8px"
      >
        <c-a :to="`/novel/${item.providerId}/${item.novelId}`">
          <span class="text-2line">
            {{ item.titleJp }}
          </span>
        </c-a>
        <div class="text-2line">{{ item.titleZh }}</div>
        <n-text depth="3">
          {{ item.type }} / 总计 {{ item.total }} / 有道 {{ item.youdao }}
          <br />
          GPT {{ item.gpt }} / Sakura {{ item.sakura }}
        </n-text>
      </n-grid-item>
    </n-grid>
    <n-empty v-if="novels.length === 0" description="空列表" />
  </template>

  <XAsyncContent
    v-else
    :loading="!error"
    :error="error?.message ?? ''"
    error-title="加载错误"
  />
</template>
