<script lang="ts" setup>
import NovelGridSkeleton from '@/components/NovelGridSkeleton.vue';
import { XAsyncContent } from '@novelia/web-kit';

import type { WenkuNovelOutlineDto } from '@/model/WenkuNovel';

defineProps<{
  novels: WenkuNovelOutlineDto[] | undefined;
  error: Error | null;
}>();
</script>

<template>
  <template v-if="novels !== undefined">
    <div class="@container">
      <div class="grid grid-cols-3 gap-3 @min-[600px]:grid-cols-6">
        <router-link
          v-for="item in novels"
          :key="item.id"
          :to="`/wenku/${item.id}`"
          class="min-w-0"
        >
          <ImageCard :src="item.cover" :title="item.titleZh || item.title" />
        </router-link>
      </div>
    </div>
    <n-empty v-if="novels.length === 0" description="空列表" />
  </template>

  <XAsyncContent
    v-else
    :loading="!error"
    :error="error?.message ?? ''"
    error-title="加载错误"
  >
    <template #loading>
      <NovelGridSkeleton panel />
    </template>
  </XAsyncContent>
</template>
