<script lang="ts" setup>
import { BookTocItemDto } from '../data/api/api_web_novel';

defineProps<{
  item: BookTocItemDto;
}>();
</script>

<template>
  <td style="width: 50%">
    {{ item.titleJp }}
    <br />
    <span class="on-mobile" style="color: grey">{{ item.titleZh }}</span>
  </td>
  <td class="on-desktop" style="color: grey">{{ item.titleZh }}</td>
</template>

<style scoped>
td {
  padding: 4px;
}
</style>
