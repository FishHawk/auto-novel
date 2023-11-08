<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { computed, ref } from 'vue';

import { ApiPersonalNovel, UserVolumes } from '@/data/api/api_personal_novel';
import { VolumeJpDto } from '@/data/api/api_wenku_novel';
import { client } from '@/data/api/client';
import { ResultState } from '@/data/result';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { TranslatorId } from '@/data/translator/translator';
import { getTranslatorLabel } from '@/data/util';
import ArchiveOutlined from '@vicons/material/es/ArchiveOutlined';

const message = useMessage();
const userData = useUserDataStore();
const setting = useSettingStore();
const readerSetting = useReaderSettingStore();

const volumesResult = ref<ResultState<UserVolumes>>();

async function loadVolume() {
  if (userData.isLoggedIn) {
    const result = await ApiPersonalNovel.listVolume();
    volumesResult.value = result;
  }
}
loadVolume();

function onFinish(_: { file: UploadFileInfo; event?: ProgressEvent }) {
  loadVolume();
}

function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过40MB');
    return false;
  }
}

const customRequest = ({
  file,
  onFinish,
  onError,
  onProgress,
}: UploadCustomRequestOptions) => {
  if (userData.token === undefined) {
    onError();
    return;
  }
  ApiPersonalNovel.createVolume(
    file.name,
    file.file as File,
    userData.token,
    (p) => onProgress({ percent: p })
  ).then((result) => {
    if (result.ok) {
      onFinish();
    } else {
      message.error(`上传失败:${result.error.message}`);
      onError();
    }
  });
};

const gptAccessToken = ref('');
const gptAccessTokenOptions = computed(() => {
  return setting.openAiAccessTokens.map((t) => {
    return { label: t, value: t };
  });
});

const sakuraEndpoint = ref('');
const sakuraEndpointOptions = computed(() => {
  return setting.sakuraEndpoints.map((t) => {
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

const taskDetails = ref<{ [key: string]: TaskDetail }>({});

async function startUpdateTask(
  volume: VolumeJpDto,
  translatorId: TranslatorId
) {
  if (taskDetails.value[`${volume.volumeId}/${translatorId}`]?.running) {
    message.info('已有任务在运行。');
    return;
  }

  const buildLabel = () => {
    let label = `${getTranslatorLabel(translatorId)}翻译`;
    if (translateExpireChapter.value) label += '[翻译过期章节]';
    return label;
  };
  taskDetails.value[`${volume.volumeId}/${translatorId}`] = {
    volumeId: volume.volumeId,
    label: buildLabel(),
    running: true,
    chapterFinished: 0,
    chapterError: 0,
    logs: [],
  };

  const getTaskDetail = () =>
    taskDetails.value[`${volume.volumeId}/${translatorId}`];

  let accessToken = gptAccessToken.value.trim();
  try {
    const obj = JSON.parse(accessToken);
    accessToken = obj.accessToken;
  } catch {}

  let sakuraEndpointValue = sakuraEndpoint.value.trim();

  const translatePersonal = (await import('@/data/translator'))
    .translatePersonal;
  await translatePersonal(
    {
      client,
      translatorId,
      volumeId: volume.volumeId,
      accessToken,
      sakuraEndpoint: sakuraEndpointValue,
      translateExpireChapter: translateExpireChapter.value,
    },
    {
      onStart: (total: number) => {
        getTaskDetail().chapterTotal = total;
      },
      onChapterSuccess: (state) => {
        if (translatorId === 'baidu') {
          volume.baidu = state;
        } else if (translatorId === 'youdao') {
          volume.youdao = state;
        } else if (translatorId === 'gpt') {
          setting.addToken(accessToken);
          volume.gpt = state;
        } else if (translatorId === 'sakura') {
          setting.addSakuraEndpoint(sakuraEndpointValue);
          volume.sakura = state;
        }
        taskDetails.value[
          `${volume.volumeId}/${translatorId}`
        ].chapterFinished += 1;
      },
      onChapterFailure: () => (getTaskDetail().chapterError += 1),
      log: (message: any) => {
        getTaskDetail().logs.push(`${message}`);
      },
    }
  );

  getTaskDetail().logs.push('\n结束');
  getTaskDetail().running = false;
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

  let downloadToken = '';
  if (volumesResult.value?.ok) {
    downloadToken = volumesResult.value.value.downloadToken;
  }

  const params = new URLSearchParams({
    translationsMode,
    downloadToken,
  });

  if (mode === 'jp' || mode === 'zh') {
    params.append('lang', 'zh');
  } else if (mode === 'mix') {
    params.append('lang', 'zh-jp');
  } else {
    params.append('lang', 'jp-zh');
  }
  translations.forEach((it) => params.append('translations', it));
  const url = `/api/personal/file/${volume.volumeId}?${params}`;

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

function translatorLabels(volume: VolumeJpDto): {
  label: string;
  translatorId: TranslatorId;
}[] {
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
    {
      label: `Sakura(${volume.sakura}/${volume.total})`,
      translatorId: 'sakura',
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
  { label: 'Sakura', value: 'sakura' },
  { label: 'GPT3', value: 'gpt' },
  { label: '有道', value: 'youdao' },
  { label: '百度', value: 'baidu' },
];

function sortVolumesJp(volumes: VolumeJpDto[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}

async function deleteVolume(volumeId: string) {
  const result = await ApiPersonalNovel.deleteVolume(volumeId);
  if (result.ok) {
    message.info('删除成功');
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function testSakura() {
  if (sakuraEndpoint.value.trim()) {
    const input =
      '国境の長いトンネルを抜けると雪国であった。夜の底が白くなった。信号所に汽車が止まった。';
    const Translator = (await import('@/data/translator')).Translator;
    const translator = await Translator.create('sakura', {
      client,
      glossary: {},
      sakuraEndpoint: sakuraEndpoint.value.trim(),
      log: () => {},
    });
    try {
      const result = await translator.translate([input]);
      const output = result[0];
      message.success(`原文：${input}\n译文：${output}`);
    } catch (e: any) {
      message.error(`Sakura报错：${e}`);
    }
  } else {
    message.error('Sakura链接为空');
  }
}
</script>

<template>
  <MainLayout>
    <n-h1>文件翻译</n-h1>

    <n-upload
      multiple
      directory-dnd
      :max="5"
      :custom-request="customRequest"
      @finish="onFinish"
      @before-upload="beforeUpload"
      style="margin-bottom: 32px"
    >
      <n-upload-dragger>
        <div style="margin-bottom: 12px">
          <n-icon size="48" :depth="3" :component="ArchiveOutlined" />
        </div>
        <n-text style="font-size: 16px">
          点击或者拖动文件到该区域来上传文件
        </n-text>
        <n-p depth="3" style="margin: 8px 0 0 0">
          支持TXT/EPUB文件，这里上传的文件是不公开的
        </n-p>
      </n-upload-dragger>
    </n-upload>

    <ResultView
      :result="volumesResult"
      :showEmpty="(it: any) => it.length === 0"
      v-slot="{ value: volumes }"
    >
      <n-button-group style="margin-bottom: 8px">
        <!-- <n-button @click="toggleTranslateOptions()"> 翻译设置 </n-button> -->
        <n-button @click="toggleDownloadOptions()">下载设置</n-button>
        <async-button @async-click="testSakura">测试Sakura</async-button>
      </n-button-group>

      <n-collapse-transition
        :show="showTranslateOptions || showDownloadOptions"
        style="margin-bottom: 16px"
      >
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
                style="height: 190px; margin-top: 8px; font-size: 12px"
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
        style="margin-bottom: 16px"
      />

      <b>
        Chrome/Edge用不了Sakura翻译，只有Firefox可以。还在研究怎么解决，如果你可以用请告诉我。
      </b>
      <n-auto-complete
        v-model:value="sakuraEndpoint"
        :options="sakuraEndpointOptions"
        placeholder="请输入你自己部署的Sakura实例的链接，例如http://127.0.0.1:5000/api/v1/generate"
        :get-show="() => true"
      />

      <n-list>
        <template v-for="volume of sortVolumesJp(volumes.volumes)">
          <n-list-item>
            <n-space vertical>
              <n-text>{{ volume.volumeId }}</n-text>
              <n-space>
                <template
                  v-for="{ translatorId, label } in translatorLabels(volume)"
                >
                  <n-button
                    text
                    type="primary"
                    @click="startUpdateTask(volume, translatorId)"
                  >
                    更新{{ label }}
                  </n-button>
                </template>

                <n-popconfirm
                  :show-icon="false"
                  @positive-click="deleteVolume(volume.volumeId)"
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
        </template>
      </n-list>
    </ResultView>
    <div v-for="detail in taskDetails">
      <TranslateTaskDetail
        :label="detail.label"
        :running="detail.running"
        :chapter-total="detail.chapterTotal"
        :chapter-finished="detail.chapterFinished"
        :chapter-error="detail.chapterError"
        :logs="detail.logs"
        style="margin-top: 20px"
      />
    </div>
  </MainLayout>
</template>

<style scoped>
.flex-container {
  display: flex;
  flex-direction: row;
}
.flex-item-left {
  padding: 16px;
  overflow-y: auto;
}
.flex-item-right {
  padding: 16px;
  overflow-y: auto;
}

@media not (max-width: 800px) {
  .flex-item-left {
    flex: 0 0 600px;
    height: calc(100vh - 32px);
    box-shadow: 0 0 4px -1px rgba(0, 0, 0, 0.2), 0 0 5px 0 rgba(0, 0, 0, 0.14),
      0 0 10px 0 rgba(0, 0, 0, 0.12);
  }
  .flex-item-right {
    flex: 1 0 600px;
    height: calc(100vh - 32px);
  }
}

@media (max-width: 800px) {
  .flex-container {
    flex-direction: column;
  }
}
</style>
