<script lang="ts" setup>
import { LogInst, useMessage } from 'naive-ui';
import { computed, nextTick, ref, watch } from 'vue';

import { client } from '@/data/api/client';
import { useSettingStore } from '@/data/stores/setting';
import { TranslatorId } from '@/data/translator/translator';
import { getTranslatorLabel } from '@/data/util';

const emit = defineEmits<{
  'update:jp': [number];
  'update:baidu': [number];
  'update:youdao': [number];
  'update:gpt': [number];
  'update:sakura': [number];
}>();

const message = useMessage();
const setting = useSettingStore();

const show = ref<boolean>(false);
const running = ref<boolean>(false);

const label = ref('');
const chapterTotal = ref<number>();
const chapterFinished = ref<number>(0);
const chapterError = ref<number>(0);
const logs = ref<string[]>([]);

const logInstRef = ref<LogInst | null>(null);
const enableAutoScroll = ref(true);
const expandLog = ref(false);

watch(logs, () => {
  if (enableAutoScroll.value) {
    nextTick(() => {
      logInstRef.value?.scrollTo({ position: 'bottom', slient: true });
    });
  }
});

const percentage = computed(() => {
  const processed = chapterFinished.value + chapterError.value;
  const total = chapterTotal.value ?? 1;
  return total === 0 ? 100 : Math.round((1000 * processed) / total) / 10;
});

const startTask = async (
  desc:
    | { type: 'web'; providerId: string; novelId: string }
    | { type: 'wenku'; novelId: string; volumeId: string }
    | { type: 'personal'; volumeId: string },
  params: {
    translatorId: TranslatorId;
    accessToken: string;
    sakuraEndpoint: string;
    translateExpireChapter: boolean;
    syncFromProvider: boolean;
    startIndex: number;
    endIndex: number;
  }
) => {
  if (running.value) {
    message.info('已有任务在运行。');
    return;
  }

  const buildLabel = () => {
    let label = `${getTranslatorLabel(params.translatorId)}翻译`;
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

  try {
    let accessToken = params.accessToken.trim();
    const obj = JSON.parse(accessToken);
    params.accessToken = obj.accessToken;
  } catch {}

  await (
    await import('@/data/translator')
  ).translate({
    client,
    ...desc,
    ...params,
    callback: {
      onStart: (total) => {
        chapterTotal.value = total;
      },
      onChapterSuccess: ({ jp, zh }) => {
        if (jp !== undefined) emit('update:jp', jp);
        if (zh !== undefined) {
          if (params.translatorId === 'baidu') {
            emit('update:baidu', zh);
          } else if (params.translatorId === 'youdao') {
            emit('update:youdao', zh);
          } else if (params.translatorId === 'gpt') {
            setting.addToken(params.accessToken);
            emit('update:gpt', zh);
          } else {
            emit('update:sakura', zh);
          }
        }
        chapterFinished.value += 1;
      },
      onChapterFailure: () => {
        chapterError.value += 1;
      },
      log: (message: any) => {
        logs.value.push(`${message}`);
      },
    },
  });

  logs.value.push('\n结束');
  running.value = false;
};

defineExpose({ startTask });
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
    <div style="display: flex">
      <n-log
        ref="logInstRef"
        :rows="expandLog ? 30 : 10"
        :lines="logs"
        style="flex: auto; margin-right: 20px"
      />
      <n-space align="center" vertical size="large" style="flex: none">
        <n-progress type="circle" :percentage="percentage" />
        <n-text>
          成功 {{ chapterFinished ?? '-' }}/{{ chapterTotal ?? '-' }}
          <br />
          失败 {{ chapterError ?? '-' }}/{{ chapterTotal ?? '-' }}
        </n-text>
      </n-space>
    </div>
  </n-card>
</template>
