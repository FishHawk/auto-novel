<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { api } from '@/data/api/api';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { TranslatorId } from '@/data/translator/translator';
import { useSettingStore } from '@/data/stores/setting';
import { useReaderSettingStore } from '@/data/stores/readerSetting';
import { getTranslatorLabel } from '@/data/util';

const props = defineProps<{
  novelId: string;
  glossary: { [key: string]: string };
  volumes: VolumeJpDto[];
}>();

const setting = useSettingStore();
const readerSetting = useReaderSettingStore();
const showModal = ref(false);

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

async function startUpdateTask(
  volume: VolumeJpDto,
  translatorId: TranslatorId
) {
  if (taskDetail.value?.running) {
    message.info('已有任务在运行。');
    return;
  }

  const buildLabel = () => {
    let label = `${getTranslatorLabel(translatorId)}翻译`;
    if (translateExpireChapter.value) label += '[翻译过期章节]';
    return label;
  };
  taskDetail.value = {
    label: buildLabel(),
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
      volumeId: volume.volumeId,
      accessToken,
      translateExpireChapter: translateExpireChapter.value,
    },
    {
      onStart: (total: number) => {
        taskDetail.value!.chapterTotal = total;
      },
      onChapterSuccess: (state) => {
        if (translatorId === 'baidu') {
          volume.baidu = state;
        } else if (translatorId === 'youdao') {
          volume.youdao = state;
        } else if (translatorId === 'gpt') {
          setting.addToken(gptAccessToken.value);
          volume.gpt = state;
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

function createDownload(volume: VolumeJpDto) {
  let ext: string;
  if (volume.volumeId.toLowerCase().endsWith('.txt')) {
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
  const url = `/api/wenku/${props.novelId}/file/${volume.volumeId}?${params}`;

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

  filename += volume.volumeId;

  return { ext, url, filename };
}

function stateToFileList(volume: VolumeJpDto): NovelFiles[] {
  return [
    {
      label: `百度(${volume.baidu}/${volume.total})`,
      translatorId: 'baidu',
    },
    {
      label: `有道(${volume.youdao}/${volume.total})`,
      translatorId: 'youdao',
    },
    {
      label: `GPT3(${volume.gpt}/${volume.total})`,
      translatorId: 'gpt',
    },
  ];
}

const showAdvanceOptions = ref(false);

const translateExpireChapter = ref(false);

async function submitGlossary() {
  const result = await ApiWenkuNovel.updateGlossary(
    props.novelId,
    props.glossary
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
}

function sortVolumesJp(volumes: VolumeJpDto[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}
</script>

<template>
  <DownloadSettingDialog v-model:show="showModal" />

  <n-text depth="3" style="font-size: 12px">
    # 翻译功能需要需要安装浏览器插件，参见
    <RouterNA to="/forum/64f3d63f794cbb1321145c07">插件使用说明</RouterNA>
  </n-text>
  <n-p>
    高级选项
    <n-switch
      :rubber-band="false"
      size="small"
      v-model:value="showAdvanceOptions"
    />
  </n-p>
  <n-collapse-transition :show="showAdvanceOptions" style="margin-bottom: 16px">
    <n-list bordered>
      <n-list-item>
        <AdvanceOptionSwitch
          title="翻译过期章节"
          description="在启动翻译任务时，重新翻译术语表过期的章节。一次性设定，默认关闭。"
          v-model:value="translateExpireChapter"
        />
      </n-list-item>
      <n-list-item>
        <AdvanceOption
          title="术语表"
          description="术语表过大可能会使得翻译质量下降（例如：百度/有道将无法从判断人名性别，导致人称代词错误）。"
        >
          <GlossaryEdit :glossary="glossary" :submit="submitGlossary" />
        </AdvanceOption>
      </n-list-item>
    </n-list>
  </n-collapse-transition>

  <n-auto-complete
    v-model:value="gptAccessToken"
    :options="gptAccessTokenOptions"
    placeholder="请输入GPT的Access Token"
    :get-show="() => true"
  />

  <n-list>
    <n-list-item v-for="volume of sortVolumesJp(volumes)">
      <n-text>{{ volume.volumeId }}</n-text>
      <br />
      <n-button-group size="small">
        <n-button
          v-for="row in stateToFileList(volume)"
          @click="startUpdateTask(volume, row.translatorId)"
        >
          更新{{ row.label }}
        </n-button>
      </n-button-group>
      <br />
      <n-button-group size="small">
        <n-button>
          <n-a
            :href="createDownload(volume).url"
            :download="createDownload(volume).filename"
            target="_blank"
          >
            下载{{ createDownload(volume).ext }}
          </n-a>
        </n-button>
        <n-button @click="showModal = true">下载设置</n-button>
      </n-button-group>
    </n-list-item>
  </n-list>

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
