<script lang="ts" setup>
import { ScrollbarInst, useMessage } from 'naive-ui';
import { computed, nextTick, ref, watch } from 'vue';

import {
  TranslateTaskDesc,
  TranslateTaskParams,
  TranslatorDesc,
} from '@/data/translator/api';
import { getTranslatorLabel } from '@/data/util';

type LogLine = { message: string; detail?: string[] };

const emit = defineEmits<{
  'update:jp': [number];
  'update:baidu': [number];
  'update:youdao': [number];
  'update:gpt': [number];
  'update:sakura': [number];
}>();

const message = useMessage();

const show = ref<boolean>(false);
const running = ref<boolean>(false);

const label = ref('');
const chapterTotal = ref<number>();
const chapterFinished = ref<number>(0);
const chapterError = ref<number>(0);
const logs = ref<LogLine[]>([]);

const logRef = ref<ScrollbarInst>();
const enableAutoScroll = ref(true);
const expandLog = ref(false);

const pushLog = (message: any, detail?: string[]) =>
  logs.value.push({ message, detail });

watch(
  logs,
  () => {
    if (enableAutoScroll.value) {
      nextTick(() => {
        logRef.value?.scrollTo({ top: Number.MAX_SAFE_INTEGER });
      });
    }
  },
  { deep: true }
);

const percentage = computed(() => {
  const processed = chapterFinished.value + chapterError.value;
  const total = chapterTotal.value ?? 1;
  return total === 0 ? 100 : Math.round((1000 * processed) / total) / 10;
});

const startTask = async (
  desc: TranslateTaskDesc,
  params: TranslateTaskParams,
  translatorDesc: TranslatorDesc,
  callback?: {
    onProgressUpdated: (progress: {
      finished: number;
      error: number;
      total: number;
    }) => void;
  }
): Promise<boolean> => {
  if (running.value) {
    message.info('已有任务在运行。');
    return false;
  }

  const buildLabel = () => {
    let label = `${getTranslatorLabel(translatorDesc.id)}翻译`;
    if (params.translateExpireChapter) label += '[翻译过期章节]';
    if (params.syncFromProvider) label += '[强制同步]';
    return label;
  };
  running.value = true;
  label.value = buildLabel();
  chapterTotal.value = undefined;
  chapterFinished.value = 0;
  chapterError.value = 0;
  logs.value = [];

  show.value = true;

  const onProgressUpdated = () =>
    callback?.onProgressUpdated({
      finished: chapterFinished.value,
      error: chapterError.value,
      total: chapterTotal.value ?? 0,
    });

  await (
    await import('@/data/translator')
  ).translate(
    desc,
    params,
    {
      onStart: (total) => {
        chapterTotal.value = total;
        onProgressUpdated();
      },
      onChapterSuccess: ({ jp, zh }) => {
        if (jp !== undefined) emit('update:jp', jp);
        if (zh !== undefined) {
          if (translatorDesc.id === 'baidu') {
            emit('update:baidu', zh);
          } else if (translatorDesc.id === 'youdao') {
            emit('update:youdao', zh);
          } else if (translatorDesc.id === 'gpt') {
            emit('update:gpt', zh);
          } else {
            emit('update:sakura', zh);
          }
        }
        chapterFinished.value += 1;
        onProgressUpdated();
      },
      onChapterFailure: () => {
        chapterError.value += 1;
        onProgressUpdated();
      },
      log: (message: string, detail?: string[]) => {
        logs.value.push({ message: `${message}`, detail });
      },
    },
    translatorDesc
  );

  pushLog('\n结束');
  logs.value.push();
  running.value = false;

  return chapterTotal.value === chapterFinished.value + chapterError.value;
};

defineExpose({ startTask });

const showLogDetailModal = ref(false);
const selectedLogDetail = ref([] as string[]);
const showDetail = (detail: string[]) => {
  selectedLogDetail.value = detail;
  showLogDetailModal.value = true;
};
</script>

<template>
  <n-card
    v-show="show"
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
    <n-flex :wrap="false">
      <n-scrollbar
        ref="logRef"
        style="flex: auto; white-space: pre-wrap"
        :style="{ height: expandLog ? '540px' : '180px' }"
      >
        <div v-for="log of logs">
          {{ log.message }}
          <span v-if="log.detail !== undefined" @click="showDetail(log.detail)">
            [详细]
          </span>
        </div>
      </n-scrollbar>
      <n-space align="center" vertical size="large" style="flex: none">
        <n-progress type="circle" :percentage="percentage" />
        <n-text>
          成功 {{ chapterFinished }}/{{ chapterTotal ?? '-' }}
          <br />
          失败 {{ chapterError }}/{{ chapterTotal ?? '-' }}
        </n-text>
      </n-space>
    </n-flex>
  </n-card>

  <card-modal title="日志详情" v-model:show="showLogDetailModal">
    <n-p v-for="line of selectedLogDetail" style="white-space: pre-wrap">
      {{ line }}
    </n-p>
  </card-modal>
</template>
