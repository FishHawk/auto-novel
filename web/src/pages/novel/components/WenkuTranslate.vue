<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { FileDownloadFilled } from '@vicons/material';

import { client } from '@/data/api/api';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { TranslatorId } from '@/data/translator/translator';
import { useSettingStore } from '@/data/stores/setting';
import { useReaderSettingStore } from '@/data/stores/readerSetting';
import { useUserDataStore } from '@/data/stores/userData';
import { getTranslatorLabel } from '@/data/util';

const props = defineProps<{
  novelId: string;
  glossary?: { [key: string]: string };
  volumes: VolumeJpDto[];
}>();

const userData = useUserDataStore();
const setting = useSettingStore();
const readerSetting = useReaderSettingStore();

const gptAccessToken = ref('');
const gptAccessTokenOptions = computed(() => {
  return setting.openAiAccessTokens.map((t) => {
    return { label: t, value: t };
  });
});

interface TaskDetail {
  volumeId: string;
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
    volumeId: volume.volumeId,
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

  const translateWenku = (await import('@/data/translator')).translateWenku;
  await translateWenku(
    {
      client,
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
          setting.addToken(accessToken);
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

const showDownloadOptions = ref(false);
const showTranslateOptions = ref(false);
const translateExpireChapter = ref(false);

function toggleTranslateOptions() {
  if (showTranslateOptions.value) {
    showTranslateOptions.value = false;
  } else {
    showTranslateOptions.value = true;
    showDownloadOptions.value = false;
  }
}

function toggleDownloadOptions() {
  if (showDownloadOptions.value) {
    showDownloadOptions.value = false;
  } else {
    showDownloadOptions.value = true;
    showTranslateOptions.value = false;
  }
}

async function submitGlossary() {
  if (props.glossary === undefined) return;
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

const modeOptions = [
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中文/日文' },
  { value: 'mix-reverse', label: '日文/中文' },
];
const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
const translationOptions = [
  { label: 'GPT3', value: 'gpt' },
  { label: '有道', value: 'youdao' },
  { label: '百度', value: 'baidu' },
];

function sortVolumesJp(volumes: VolumeJpDto[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}

async function deleteVolume(novelId: string, volumeId: string) {
  const result = await ApiWenkuNovel.deleteVolume(novelId, volumeId);
  if (result.ok) {
    message.info('删除成功');
  } else {
    message.error('删除失败：' + result.error.message);
  }
}
</script>

<template>
  <n-p depth="3" style="font-size: 12px">
    # 翻译功能需要需要安装浏览器插件，参见
    <RouterNA to="/forum/64f3d63f794cbb1321145c07">插件使用说明</RouterNA>
  </n-p>
  <n-button-group style="margin-bottom: 8px">
    <n-button v-if="glossary" @click="toggleTranslateOptions()">
      翻译设置
    </n-button>
    <n-button @click="toggleDownloadOptions()">下载设置</n-button>
  </n-button-group>

  <n-collapse-transition
    :show="showTranslateOptions || showDownloadOptions"
    style="margin-bottom: 16px"
  >
    <n-list v-if="showTranslateOptions && glossary" bordered>
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

    <n-list v-if="showDownloadOptions" bordered>
      <n-list-item>
        <AdvanceOptionSwitch
          title="下载文件格式与阅读设置一致"
          description="使用在线章节的阅读设置作为下载文件的格式，启用时会禁止下面的自定义设置。"
          v-model:value="setting.isDownloadFormatSameAsReaderFormat"
        />
      </n-list-item>

      <n-list-item>
        <AdvanceOptionRadio
          title="自定义下载文件语言"
          description="设置下载文件的语言。注意部分Epub阅读器不支持自定义字体颜色，日文段落会被强制使用黑色字体。"
          v-model:value="setting.downloadFormat.mode"
          :disabled="setting.isDownloadFormatSameAsReaderFormat"
          :options="modeOptions"
        />
      </n-list-item>

      <n-list-item>
        <AdvanceOptionRadio
          title="自定义下载文件翻译"
          description="设置下载文件使用的翻译。注意右侧选中的翻译的顺序，优先模式顺序代表优先级，并列模式顺序代表翻译的排列顺序。"
          v-model:value="setting.downloadFormat.translationsMode"
          :disabled="setting.isDownloadFormatSameAsReaderFormat"
          :options="translationModeOptions"
        >
          <n-transfer
            v-model:value="setting.downloadFormat.translations"
            :options="translationOptions"
            :disabled="setting.isDownloadFormatSameAsReaderFormat"
            size="small"
            style="height: 160px; margin-top: 8px; font-size: 12px"
          />
        </AdvanceOptionRadio>
      </n-list-item>
    </n-list>
  </n-collapse-transition>

  <n-auto-complete
    v-model:value="gptAccessToken"
    :options="gptAccessTokenOptions"
    placeholder="请输入ChatGPT的Access Token或者Api Key"
    :get-show="() => true"
  />

  <n-list>
    <template v-for="volume of sortVolumesJp(volumes)">
      <n-list-item>
        <n-space vertical>
          <n-text>{{ volume.volumeId }}</n-text>
          <n-space>
            <n-button
              v-for="row in stateToFileList(volume)"
              text
              type="primary"
              @click="startUpdateTask(volume, row.translatorId)"
            >
              更新{{ row.label }}
            </n-button>

            <n-popconfirm
              v-if="userData.asAdmin || novelId.startsWith('user')"
              :show-icon="false"
              @positive-click="deleteVolume(novelId, volume.volumeId)"
              :negative-text="null"
            >
              <template #trigger>
                <n-button text type="error"> 删除 </n-button>
              </template>
              真的要删除{{ volume.volumeId }}吗？
            </n-popconfirm>
          </n-space>
        </n-space>
        <template #suffix>
          <n-a
            :href="createDownload(volume).url"
            :download="createDownload(volume).filename"
            target="_blank"
          >
            <n-button>
              <template #icon>
                <n-icon :component="FileDownloadFilled" />
              </template>
              下载
            </n-button>
          </n-a>
        </template>
      </n-list-item>

      <TranslateTaskDetail
        v-if="taskDetail && taskDetail.volumeId === volume.volumeId"
        :label="taskDetail.label"
        :running="taskDetail.running"
        :chapter-total="taskDetail.chapterTotal"
        :chapter-finished="taskDetail.chapterFinished"
        :chapter-error="taskDetail.chapterError"
        :logs="taskDetail.logs"
        style="margin-top: 20px"
      />
    </template>
  </n-list>
</template>
