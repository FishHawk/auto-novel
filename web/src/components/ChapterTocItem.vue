<script lang="ts" setup>
import { NText, NButton, NIcon } from 'naive-ui';
import { KeyboardArrowUpRound, KeyboardArrowDownRound } from '@vicons/material';
import CA from '@/components/CA.vue';

import { ReadableTocItem } from '@/pages/novel/components/common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  tocItem: ReadableTocItem;
  lastRead?: string;
  isSeparator: boolean;
  isExpanded?: boolean;
}>();

const emit = defineEmits<{
  (e: 'toggleExpand'): void;
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

const vars = useThemeVars();
const mixColor = () => {
  const color = vars.value.primaryColor;
  const r = parseInt(color.substring(1, 3), 16);
  const g = parseInt(color.substring(3, 5), 16);
  const b = parseInt(color.substring(5, 7), 16);

  const p = 0.5;
  const mr = (r * p + 255 * (1 - p)).toFixed(0);
  const mg = (g * p).toFixed(0);
  const mb = (b * p).toFixed(0);
  return `rgb(${mr}, ${mg}, ${mb})`;
};
const visitedColor = mixColor();
</script>

<template>
  <component
    :is="!isSeparator ? CA : 'div'"
    :to="
      !isSeparator
        ? `/novel/${providerId}/${novelId}/${tocItem.chapterId}`
        : undefined
    "
    class="toc"
    style="width: calc(100% - 12px); display: block; padding: 6px"
    :style="{ 'font-size': !isSeparator ? '14px' : '12px' }"
  >
    <div
      style="display: flex; align-items: center; justify-content: space-between"
    >
      <div>
        <n-text
          :class="{
            'toc-title-visited': type !== undefined && type !== 'warning',
            'toc-title': type !== undefined,
          }"
          :type="type"
        >
          {{ tocItem.titleJp }}
        </n-text>
        <br />
        <n-text depth="3">
          {{ tocItem.titleZh }}
        </n-text>
        <br />
        <n-text v-if="!isSeparator" depth="3" style="font-size: 12px">
          [{{ tocItem.order }}]
          <n-time
            v-if="tocItem.createAt"
            :time="tocItem.createAt * 1000"
            format="yyyy-MM-dd HH:mm"
          />
        </n-text>
      </div>
      <n-button
        v-if="isSeparator"
        text
        style="
          font-size: 20px;
          padding: 10px 0 10px 20px;
          margin: -10px 0 -10px -20px;
        "
        @click.stop="emit('toggleExpand')"
      >
        <n-icon>
          <component
            :is="isExpanded ? KeyboardArrowUpRound : KeyboardArrowDownRound"
          />
        </n-icon>
      </n-button>
    </div>
  </component>
</template>

<style scoped>
.toc {
  cursor: default;
}
.toc:hover .toc-title {
  text-decoration: underline;
}
.toc:visited .toc-title-visited {
  color: v-bind('visitedColor');
}
</style>
