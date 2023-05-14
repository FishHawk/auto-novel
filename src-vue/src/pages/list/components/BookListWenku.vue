<script lang="ts" setup>
import { WenkuListPageDto } from '@/data/api/api_wenku_novel';

defineProps<{
  page: WenkuListPageDto;
}>();
</script>

<template>
  <n-grid :x-gap="12" :y-gap="12" cols="2 600:4">
    <n-grid-item v-for="item in page.items">
      <n-a :href="`/wenku/${item.id}`" target="_blank">
        <ImageCard
          :src="item.cover"
          :title="item.titleZh ? item.titleZh : item.title"
        />
      </n-a>
    </n-grid-item>
  </n-grid>
</template>
