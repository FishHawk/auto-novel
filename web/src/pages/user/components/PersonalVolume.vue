<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { ApiUserPersonal, PersonalVolume } from '@/data/api/api_user_personal';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useSettingStore } from '@/data/stores/setting';
import { useSakuraWorkspaceStore } from '@/data/stores/workspace';
import { TranslatorId } from '@/data/translator/translator';

const { volume, downloadToken, getParams } = defineProps<{
  volume: PersonalVolume;
  downloadToken: string;
  getParams: () => {
    accessToken: string;
    translateExpireChapter: boolean;
  };
}>();

const message = useMessage();
const sakuraWorkspace = useSakuraWorkspaceStore();
const setting = useSettingStore();
const readerSetting = useReaderSettingStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: TranslatorId) => {
  const params = getParams();
  return translateTask?.value?.startTask(
    { type: 'personal', volumeId: volume.volumeId },
    {
      ...params,
      translatorId,
      sakuraEndpoint: '',
      syncFromProvider: false,
      startIndex: 0,
      endIndex: 65536,
    }
  );
};

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

const submitSakuraJob = () => {
  const { translateExpireChapter } = getParams();

  const taskString = `personal/${volume.volumeId}`;

  const params: { [key: string]: string } = {};
  if (translateExpireChapter) {
    params['expire'] = `${translateExpireChapter}`;
  }
  const searchParams = new URLSearchParams(params).toString();
  const queryString = searchParams ? `?${searchParams}` : '';

  const task = taskString + queryString;

  const success = sakuraWorkspace.addJob({
    task,
    description: volume.volumeId,
    createAt: Date.now(),
  });
  if (success) {
    message.success('排队成功');
  } else {
    message.error('Sakura翻译任务已经存在');
  }
};

const showGlossaryEditor = ref(false);
</script>

<template>
  <div>
    <n-space align="center" justify="space-between" :wrap="false">
      <n-space vertical>
        <n-text>{{ volume.volumeId }}</n-text>
        <n-space>
          <template v-for="{ translatorId, label } of translatorLabels">
            <n-button
              v-if="translatorId !== 'sakura'"
              text
              type="primary"
              @click="startTranslateTask(translatorId)"
            >
              更新{{ label }}
            </n-button>

            <n-button
              v-else
              text
              type="primary"
              @click="() => submitSakuraJob()"
            >
              私人排队{{ label }}
            </n-button>
          </template>

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

    <TranslateTask
      ref="translateTask"
      @update:baidu="(zh) => (volume.baidu = zh)"
      @update:youdao="(zh) => (volume.youdao = zh)"
      @update:gpt="(zh) => (volume.gpt = zh)"
      @update:sakura="(zh) => (volume.sakura = zh)"
      style="margin-top: 20px"
    />
  </div>
</template>
