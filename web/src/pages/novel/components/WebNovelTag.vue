<script lang="ts" setup>
import { tryTranslateKeyword } from '@/data/util_web';

defineProps<{
  tag: string;
  attention: boolean;
}>();
</script>

<template>
  <router-link :to="`/novel-list?query=${tag}\$`">
    <n-tag :bordered="false" size="small" style="cursor: pointer">
      <template v-if="attention">
        <b>{{ tag }}</b>
      </template>
      <template v-else>
        {{ tryTranslateKeyword(tag) }}
      </template>
    </n-tag>
  </router-link>
</template>
