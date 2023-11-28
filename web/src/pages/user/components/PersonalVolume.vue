<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { ApiUserPersonal, PersonalVolume } from '@/data/api/api_user_personal';
import { VolumeJpDto } from '@/data/api/api_wenku_novel';
import { client } from '@/data/api/client';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useSettingStore } from '@/data/stores/setting';
import { TranslatorId } from '@/data/translator/translator';
import { getTranslatorLabel } from '@/data/util';

const { volume, downloadToken, getOptions } = defineProps<{
  volume: PersonalVolume;
  downloadToken: string;
  getOptions: () => {
    translateExpireChapter: boolean;
    gptAccessToken: string;
    sakuraEndpoint: string;
  };
}>();

const message = useMessage();
const setting = useSettingStore();
const readerSetting = useReaderSettingStore();

interface TaskDetail {
  volumeId: string;
  label: string;
  running: boolean;
  chapterTotal?: number;
  chapterFinished: number;
  chapterError: number;
  logs: string[];
}

const taskDetail = ref<TaskDetail>();

const translatorLabels = computed(
  () =>
    <{ label: string; translatorId: TranslatorId }[]>[
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
    ]
);

const downloadFile = computed(() => {
  const { mode, translationsMode, translations } =
    setting.isDownloadFormatSameAsReaderFormat
      ? readerSetting
      : setting.downloadFormat;

  let lang: 'zh' | 'zh-jp' | 'jp-zh';
  if (mode === 'jp' || mode === 'zh') {
    lang = 'zh';
  } else if (mode === 'mix') {
    lang = 'zh-jp';
  } else {
    lang = 'jp-zh';
  }

  const { url, filename } = ApiUserPersonal.createFileUrl({
    volumeId: volume.volumeId,
    lang,
    translationsMode,
    translations,
    downloadToken,
  });

  return { url, filename };
});

async function startUpdateTask(
  volume: VolumeJpDto,
  translatorId: TranslatorId
) {
  if (taskDetail.value?.running) {
    message.info('已有任务在运行。');
    return;
  }

  const { translateExpireChapter, gptAccessToken, sakuraEndpoint } =
    getOptions();

  const buildLabel = () => {
    let label = `${getTranslatorLabel(translatorId)}翻译`;
    if (translateExpireChapter) label += '[翻译过期章节]';
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

  let accessToken = gptAccessToken.trim();
  try {
    const obj = JSON.parse(accessToken);
    accessToken = obj.accessToken;
  } catch {}

  let sakuraEndpointValue = sakuraEndpoint.trim();

  const translatePersonal = (await import('@/data/translator'))
    .translatePersonal;
  await translatePersonal(
    {
      client,
      translatorId,
      volumeId: volume.volumeId,
      accessToken,
      sakuraEndpoint: sakuraEndpointValue,
      translateExpireChapter,
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
        } else if (translatorId === 'sakura') {
          setting.addSakuraEndpoint(sakuraEndpointValue);
          volume.sakura = state;
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

async function deleteVolume(volumeId: string) {
  const result = await ApiUserPersonal.deleteVolume(volumeId);
  if (result.ok) {
    message.info('删除成功');
  } else {
    message.error('删除失败：' + result.error.message);
  }
}

async function submitGlossary(
  volumeId: string,
  glossary: { [key: string]: string }
) {
  const result = await ApiUserPersonal.updateGlossary(volumeId, glossary);
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
}

const showGlossaryEditor = ref(false);
</script>

<template>
  <div>
    <n-space align="center" justify="space-between" :wrap="false">
      <n-space vertical>
        <n-text>{{ volume.volumeId }}</n-text>
        <n-space>
          <n-button
            v-for="{ translatorId, label } in translatorLabels"
            text
            type="primary"
            @click="startUpdateTask(volume, translatorId)"
          >
            更新{{ label }}
          </n-button>
          <n-button
            text
            type="primary"
            @click="showGlossaryEditor = !showGlossaryEditor"
          >
            编辑术语表
          </n-button>

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
      <n-a
        :href="downloadFile.url"
        :download="downloadFile.filename"
        target="_blank"
      >
        <n-button>
          <template #icon>
            <n-icon :component="FileDownloadFilled" />
          </template>
          下载
        </n-button>
      </n-a>
    </n-space>

    <n-collapse-transition :show="showGlossaryEditor" style="margin-top: 16px">
      <n-list bordered>
        <n-list-item>
          <AdvanceOption
            title="术语表"
            description="术语表过大可能会使得翻译质量下降（例如：百度/有道将无法从判断人名性别，导致人称代词错误）。"
          >
            <GlossaryEdit
              :glossary="volume.glossary"
              :submit="() => submitGlossary(volume.volumeId, volume.glossary)"
            />
          </AdvanceOption>
        </n-list-item>
      </n-list>
    </n-collapse-transition>

    <TranslateTaskDetail
      v-if="taskDetail"
      :label="taskDetail.label"
      :running="taskDetail.running"
      :chapter-total="taskDetail.chapterTotal"
      :chapter-finished="taskDetail.chapterFinished"
      :chapter-error="taskDetail.chapterError"
      :logs="taskDetail.logs"
      style="margin-top: 16px"
    />
  </div>
</template>
