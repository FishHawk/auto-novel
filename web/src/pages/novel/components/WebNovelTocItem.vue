<script lang="ts" setup>
import { NText } from 'naive-ui';

import CA from '@/pages/components/CA.vue';

import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  tocItem: ReadableTocItem;
  lastRead?: string;
}>();

const type = computed(() => {
  const { tocItem, lastRead } = props;
  if (tocItem.chapterId === undefined) {
    return undefined;
  } else {
    const isLastReader =
      lastRead !== undefined && tocItem.chapterId === lastRead;
    if (isLastReader) {
      return 'warning';
    } else {
      return 'success';
    }
  }
});
</script>

<template>
  <component
    :is="tocItem.order !== undefined ? CA : 'div'"
    :to="`/novel/${providerId}/${novelId}/${tocItem.chapterId}`"
    class="toc"
    style="width: calc(100% - 12px); display: block; padding: 6px"
    :style="{ 'font-size': tocItem.order !== undefined ? '14px' : '12px' }"
  >
    <n-text :class="{ 'toc-title': type !== undefined }" :type="type">
      {{ tocItem.titleJp }}
    </n-text>
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

<style>
.toc:hover .toc-title {
  text-decoration: underline;
}
</style>
