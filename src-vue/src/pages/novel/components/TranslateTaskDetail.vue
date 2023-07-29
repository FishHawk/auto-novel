<script lang="ts" setup>
import { computed, nextTick, ref, watch } from 'vue';
import { LogInst } from 'naive-ui';

const props = defineProps<{
  label: string;
  running: boolean;
  chapterTotal?: number;
  chapterFinished: number;
  chapterError: number;
  logs: string[];
}>();

const logInstRef = ref<LogInst | null>(null);
const enableAutoScroll = ref(true);
const expandLog = ref(false);

watch(props.logs, () => {
  if (enableAutoScroll.value) {
    nextTick(() => {
      logInstRef.value?.scrollTo({ position: 'bottom', slient: true });
    });
  }
});

const percentage = computed(() => {
  const processed = props.chapterFinished + props.chapterError;
  const total = props.chapterTotal ?? 1;
  if (total == 0) return 100;
  else return Math.round((1000 * processed) / total) / 10;
});
</script>

<template>
  <n-card
    :title="`${label} [${running ? '运行中' : '已结束'}]`"
    embedded
    :bordered="false"
  >
    <template #header-extra>
      <n-space align="center">
        <n-button size="small" @click="enableAutoScroll = !enableAutoScroll">
          {{ enableAutoScroll ? '暂停滚动' : '自动滚动' }}
        </n-button>
        <n-button size="small" @click="expandLog = !expandLog">
          {{ expandLog ? '收起日志' : '展开日志' }}
        </n-button>
      </n-space>
    </template>
    <div style="display: flex">
      <n-log
        ref="logInstRef"
        :rows="expandLog ? 30 : 10"
        :lines="logs"
        style="flex: auto; margin-right: 20px"
      />
      <n-space align="center" vertical size="large" style="flex: none">
        <n-progress type="circle" :percentage="percentage" />
        <div>
          <span>
            成功 {{ chapterFinished ?? '-' }}/{{ chapterTotal ?? '-' }}
          </span>
          <br />
          <span>
            失败 {{ chapterError ?? '-' }}/{{ chapterTotal ?? '-' }}
          </span>
        </div>
      </n-space>
    </div>
  </n-card>
</template>
