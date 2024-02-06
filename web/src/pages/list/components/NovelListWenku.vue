<script lang="ts" setup>
import { WenkuNovelOutlineDto } from '@/data/api/api_wenku_novel';

defineProps<{
  items: WenkuNovelOutlineDto[];
}>();
</script>

<template>
  <n-grid :x-gap="12" :y-gap="12" cols="2 600:4">
    <n-grid-item v-for="item in items">
      <router-link :to="`/wenku/${item.id}`">
        <ImageCard
          :src="item.cover"
          :title="item.titleZh ? item.titleZh : item.title"
        />
      </router-link>
    </n-grid-item>
  </n-grid>
</template>
