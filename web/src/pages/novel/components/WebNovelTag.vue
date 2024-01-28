<script lang="ts" setup>
import { tryTranslateKeyword } from '@/data/util_web';

defineProps<{
  tag: string;
  attention: boolean;
}>();
</script>

<template>
  <router-link
    :to="`/novel-list?query=${tag}\$`"
    style="color: rgb(51, 54, 57)"
  >
    <n-tag :bordered="false" size="small">
      <template v-if="attention">
        <b>{{ tag }}</b>
      </template>
      <template v-else>
        {{ tryTranslateKeyword(tag) }}
      </template>
    </n-tag>
  </router-link>
</template>
