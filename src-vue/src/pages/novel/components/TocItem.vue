<script lang="ts" setup>
import { WebNovelTocItemDto } from '@/data/api/api_web_novel';

const props = defineProps<{
  item: WebNovelTocItemDto;
  desktop: boolean;
}>();

const date = props.item.createAt
  ? new Date(props.item.createAt * 1000).toLocaleString('zh-CN', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
    })
  : undefined;
</script>

<template>
  <template v-if="desktop">
    <div style="width: 100; display: flex; padding: 6px">
      <span style="flex: 1 1 0">{{ item.titleJp }}</span>
      <span style="color: grey; flex: 1 1 0">{{ item.titleZh }}</span>
      <span style="color: grey; width: 110px">{{ date }}</span>
    </div>
  </template>

  <template v-else>
    <div style="width: 100; padding: 6px">
      {{ item.titleJp }}
      <br />
      <span style="color: grey">{{ item.titleZh }}</span>
      <br />
      <span style="color: grey">{{ date }}</span>
    </div>
  </template>
</template>
