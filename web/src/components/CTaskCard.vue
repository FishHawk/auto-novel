<script lang="ts" setup>
import { ScrollbarInst } from 'naive-ui';

const props = defineProps<{
  title: string;
  running: boolean;
}>();

const show = ref(false);
watch(
  () => props.running,
  (running) => {
    if (running) {
      show.value = true;
    }
  },
);

type LogLine = { message: string; detail?: string[] };
const logs = ref<LogLine[]>([]);

const clearLog = () => {
  logs.value = [];
};
const pushLog = (line: LogLine) => {
  logs.value.push(line);
};

const logRef = ref<ScrollbarInst>();
const enableAutoScroll = ref(true);
const expandLog = ref(false);

watch(
  logs,
  () => {
    if (enableAutoScroll.value) {
      nextTick(() => {
        logRef.value?.scrollTo({ top: Number.MAX_SAFE_INTEGER });
      });
    }
  },
  { deep: true },
);

const showLogDetailModal = ref(false);
const selectedLogDetail = ref([] as string[]);
const selectedLogMessage = ref('');
const showDetail = (message: string, detail: string[]) => {
  selectedLogMessage.value = message.trim();
  selectedLogDetail.value = detail;
  showLogDetailModal.value = true;
};

defineExpose({
  clearLog,
  pushLog,
  hide: () => {
    show.value = false;
  },
});
</script>

<template>
  <n-card
    v-show="show"
    :title="`${title} [${running ? '运行中' : '已结束'}]`"
    embedded
    :bordered="false"
  >
    <template #header-extra>
      <n-flex align="center">
        <c-button
          :label="enableAutoScroll ? '暂停滚动' : '自动滚动'"
          size="small"
          @action="enableAutoScroll = !enableAutoScroll"
        />
        <c-button
          :label="expandLog ? '收起日志' : '展开日志'"
          size="small"
          @action="expandLog = !expandLog"
        />
      </n-flex>
    </template>
    <n-flex :wrap="false">
      <n-scrollbar
        ref="logRef"
        style="flex: auto; white-space: pre-wrap"
        :style="{ height: expandLog ? '540px' : '180px' }"
      >
        <div v-for="log of logs">
          {{ log.message }}
          <span
            v-if="log.detail"
            @click="showDetail(log.message, log.detail!!)"
          >
            [详细]
          </span>
        </div>
      </n-scrollbar>
      <slot />
    </n-flex>

    <c-modal
      :title="`日志详情 - ${selectedLogMessage}`"
      v-model:show="showLogDetailModal"
    >
      <n-p v-for="line of selectedLogDetail" style="white-space: pre-wrap">
        {{ line }}
      </n-p>
    </c-modal>
  </n-card>
</template>
