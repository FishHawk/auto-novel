<script lang="ts" setup>
import { NText } from 'naive-ui';
import { RouterLink } from 'vue-router';

import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  tocItem: ReadableTocItem;
  lastRead?: string;
  showLastRead?: boolean;
}>();

const type = (() => {
  if (props.tocItem.chapterId === undefined) {
    return 'default';
  } else if (props.tocItem.chapterId === props.lastRead) {
    return 'warning';
  } else {
    return 'success';
  }
})();

const itemRef = ref<HTMLDivElement>();
if (props.showLastRead) {
  onMounted(() => {
    if (type === 'warning') {
      itemRef.value?.scrollIntoView();
    }
  });
}
</script>

<template>
  <component
    :is="tocItem.order !== undefined ? RouterLink : 'div'"
    :to="`/novel/${providerId}/${novelId}/${tocItem.chapterId}`"
    class="toc"
    style="width: calc(100% - 12px); display: block; padding: 6px"
    :style="{ 'font-size': tocItem.order !== undefined ? '14px' : '12px' }"
  >
    <div ref="itemRef" />
    <n-text :type="type">
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
