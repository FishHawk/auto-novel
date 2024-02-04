<script lang="ts" setup>
import { NA, NText } from 'naive-ui';

import { ReadableTocItem } from './common';

defineProps<{
  providerId: string;
  novelId: string;
  tocItem: ReadableTocItem;
}>();
</script>

<template>
  <component
    :is="tocItem.order !== undefined ? NA : NText"
    :href="`/novel/${providerId}/${novelId}/${tocItem.chapterId}`"
    class="toc"
    style="width: calc(100% - 12px); display: block; padding: 6px"
    :style="{ 'font-size': tocItem.order !== undefined ? '14px' : '12px' }"
  >
    {{ tocItem.titleJp }}
    <br />
    <n-text depth="3">
      {{ tocItem.titleZh }}
    </n-text>
    <br />
    <n-text
      v-if="tocItem.order !== undefined"
      depth="3"
      style="font-size: 12px"
    >
      [{{ tocItem.order }}]
      <n-time
        v-if="tocItem.createAt"
        :time="tocItem.createAt * 1000"
        format="yyyy-MM-dd HH:mm"
      />
    </n-text>
  </component>
</template>
