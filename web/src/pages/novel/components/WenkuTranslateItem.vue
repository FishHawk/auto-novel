<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { api } from '@/data/api/api';
import { VolumeJpDto } from '@/data/api/api_wenku_novel';
import { TranslatorId } from '@/data/translator/translator';
import { useSettingStore } from '@/data/stores/setting';
import { useReaderSettingStore } from '@/data/stores/readerSetting';
import { getTranslatorLabel } from '@/data/util';

const props = defineProps<{
  novelId: string;
  volume: VolumeJpDto;
}>();

const emits = defineEmits<{ openSetting: [] }>();

const setting = useSettingStore();
const readerSetting = useReaderSettingStore();

const gptAccessToken = ref('');
const gptAccessTokenOptions = computed(() => {
  return setting.openAiAccessTokens.map((t) => {
    return { label: t, value: t };
  });
});

interface TaskDetail {
  label: string;
  running: boolean;
  chapterTotal?: number;
  chapterFinished: number;
  chapterError: number;
  logs: string[];
}

const message = useMessage();

const taskDetail: Ref<TaskDetail | undefined> = ref();

async function startUpdateTask(translatorId: TranslatorId) {
  if (taskDetail.value?.running) {
    message.info('已有任务在运行。');
    return;
  }

  const label = `${getTranslatorLabel(translatorId)}翻译`;
  taskDetail.value = {
    label,
    running: true,
    chapterFinished: 0,
    chapterError: 0,
    logs: [],
  };

  let accessToken = gptAccessToken.value.trim();
  try {
    const obj = JSON.parse(accessToken);
    accessToken = obj.accessToken;
  } catch {}

  const translateWenku = (await import('@/data/translator/translator'))
    .translateWenku;
  await translateWenku(
    {
      api,
      novelId: props.novelId,
      translatorId,
      volumeId: props.volume.volumeId,
      accessToken,
      translateExpireChapter: false,
    },
    {
      onStart: (total: number) => {
        taskDetail.value!.chapterTotal = total;
      },
      onChapterSuccess: (state) => {
        if (translatorId === 'baidu') {
          props.volume.baidu = state;
        } else if (translatorId === 'youdao') {
          props.volume.youdao = state;
        } else if (translatorId === 'gpt') {
          setting.addToken(gptAccessToken.value);
          props.volume.gpt = state;
        }
        taskDetail.value!.chapterFinished += 1;
      },
      onChapterFailure: () => (taskDetail.value!.chapterError += 1),
      log: (message: any) => {
        taskDetail.value!.logs.push(`${message}`);
      },
    }
  );

  taskDetail.value!.logs.push('\n结束');
  taskDetail.value!.running = false;
}

interface NovelFiles {
  label: string;
  translatorId: TranslatorId;
}

const volumeDownload = computed(() => {
  let ext: string;
  if (props.volume.volumeId.toLowerCase().endsWith('.txt')) {
    ext = 'txt';
  } else {
    ext = 'epub';
  }
  ext = ext.toUpperCase();

  const { mode, translationsMode, translations } =
    setting.isDownloadFormatSameAsReaderFormat
      ? readerSetting
      : setting.downloadFormat;
  const params = new URLSearchParams({
    translationsMode,
  });

  if (mode === 'jp' || mode === 'zh') {
    params.append('lang', 'zh');
  } else if (mode === 'mix') {
    params.append('lang', 'zh-jp');
  } else {
    params.append('lang', 'jp-zh');
  }
  translations.forEach((it) => params.append('translations', it));
  const url = `/api/wenku/${props.novelId}/file/${props.volume.volumeId}?${params}`;

  let filename = '';
  if (mode === 'jp' || mode === 'zh') {
    filename += 'zh';
  } else if (mode === 'mix') {
    filename += 'zh-jp';
  } else {
    filename += 'jp-zh';
  }
  filename += '.';

  if (translationsMode === 'parallel') {
    filename += 'B';
  } else {
    filename += 'Y';
  }
  translations.forEach((it) => (filename += it[0]));
  filename += '.';

  filename += props.volume.volumeId;

  return { ext, url, filename };
});

function stateToFileList(): NovelFiles[] {
  return [
    {
      label: `百度(${props.volume.baidu}/${props.volume.total})`,
      translatorId: 'baidu',
    },
    {
      label: `有道(${props.volume.youdao}/${props.volume.total})`,
      translatorId: 'youdao',
    },
    {
      label: `GPT3(${props.volume.gpt}/${props.volume.total})`,
      translatorId: 'gpt',
    },
  ];
}
</script>

<template>
  <n-space vertical>
    <n-auto-complete
      v-model:value="gptAccessToken"
      :options="gptAccessTokenOptions"
      placeholder="请输入GPT的Access Token"
      :get-show="() => true"
    />
    <n-button-group>
      <n-button
        v-for="row in stateToFileList()"
        @click="startUpdateTask(row.translatorId)"
      >
        更新{{ row.label }}
      </n-button>
    </n-button-group>
    <n-button-group>
      <n-button>
        <n-a
          :href="volumeDownload.url"
          :download="volumeDownload.filename"
          target="_blank"
        >
          下载{{ volumeDownload.ext }}
        </n-a>
      </n-button>
      <n-button @click="emits('openSetting')">下载设置</n-button>
    </n-button-group>
  </n-space>

  <TranslateTaskDetail
    v-if="taskDetail"
    :label="taskDetail.label"
    :running="taskDetail.running"
    :chapter-total="taskDetail.chapterTotal"
    :chapter-finished="taskDetail.chapterFinished"
    :chapter-error="taskDetail.chapterError"
    :logs="taskDetail.logs"
    style="margin-top: 20px"
  />
</template>
