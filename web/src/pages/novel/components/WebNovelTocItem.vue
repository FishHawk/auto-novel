<script lang="ts" setup>
import { NText } from 'naive-ui';
import { RouterLink } from 'vue-router';

import CA from '@/pages/components/CA.vue';

import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  tocItem: ReadableTocItem;
  lastRead?: string;
  showLastRead?: boolean;
}>();

const isLastReader =
  props.lastRead !== undefined && props.tocItem.chapterId === props.lastRead;

const itemRef = ref<HTMLDivElement>();
if (props.showLastRead) {
  onMounted(() => {
    if (isLastReader) {
      itemRef.value?.scrollIntoView();
    }
  });
}
</script>

<template>
  <component
    :is="tocItem.order !== undefined ? CA : 'div'"
    :to="`/novel/${providerId}/${novelId}/${tocItem.chapterId}`"
    class="toc"
    style="width: calc(100% - 12px); display: block; padding: 6px"
    :style="{ 'font-size': tocItem.order !== undefined ? '14px' : '12px' }"
  >
    <div ref="itemRef" />

    <template v-if="!isLastReader">
      {{ tocItem.titleJp }}
    </template>
    <n-text v-else type="warning">
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
