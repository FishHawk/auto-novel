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
    <div class="@container">
      <div class="grid grid-cols-1 gap-3 @min-[850px]:grid-cols-4">
        <div
          v-for="item in novels"
          :key="`${item.providerId}/${item.novelId}`"
          class="min-w-0 p-2"
        >
          <c-a :to="`/novel/${item.providerId}/${item.novelId}`">
            <span class="text-2line">
              {{ item.titleJp }}
            </span>
          </c-a>
          <div class="text-2line">{{ item.titleZh }}</div>
          <span class="text-muted">
            {{ item.type }} / 总计 {{ item.total }} / 有道 {{ item.youdao }}
            <br />
            GPT {{ item.gpt }} / Sakura {{ item.sakura }}
          </span>
        </div>
      </div>
    </div>
    <n-empty v-if="novels.length === 0" description="空列表" />
  </template>

  <XAsyncContent
    v-else
    :loading="!error"
    :error="error?.message ?? ''"
    error-title="加载错误"
  />
</template>
