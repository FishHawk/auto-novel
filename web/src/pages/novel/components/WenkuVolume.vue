<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { ApiSakura } from '@/data/api/api_sakura';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { TranslatorId } from '@/data/translator/translator';

const { novelId, volume, getParams } = defineProps<{
  novelId: string;
  volume: VolumeJpDto;
  getParams: () => {
    accessToken: string;
    translateExpireChapter: boolean;
  };
}>();

const emits = defineEmits<{ deleted: [] }>();

const message = useMessage();
const userData = useUserDataStore();
const setting = useSettingStore();
const readerSetting = useReaderSettingStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: TranslatorId) => {
  const params = getParams();
  return translateTask?.value?.startTask(
    { type: 'wenku', novelId, volumeId: volume.volumeId },
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

const file = computed(() => {
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

  const { url, filename } = ApiWenkuNovel.createFileUrl({
    novelId,
    volumeId: volume.volumeId,
    lang,
    translationsMode,
    translations,
  });
  return { url, filename };
});

const submitSakuraJob = async () => {
  const { translateExpireChapter } = getParams();
  const result = await ApiSakura.createSakuraJobWenkuTranslate(
    novelId,
    volume.volumeId,
    {
      start: 0,
      end: 65535,
      expire: translateExpireChapter,
    }
  );
  if (result.ok) {
    message.info('排队成功');
  } else {
    message.error('排队失败:' + result.error.message);
  }
};

const deleteVolume = async () => {
  const result = await ApiWenkuNovel.deleteVolume(novelId, volume.volumeId);
  if (result.ok) {
    emits('deleted');
    message.info('删除成功');
  } else {
    message.error('删除失败：' + result.error.message);
  }
};
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

            <async-button
              v-else
              text
              type="primary"
              @async-click="() => submitSakuraJob()"
            >
              排队{{ label }}
            </async-button>
          </template>
          <RouterNA to="/sakura">
            <n-button text type="primary"> 查看队列 </n-button>
          </RouterNA>

          <n-popconfirm
            v-if="userData.asAdmin"
            :show-icon="false"
            @positive-click="deleteVolume()"
            :negative-text="null"
          >
            <template #trigger>
              <n-button text type="error"> 删除 </n-button>
            </template>
            真的要删除{{ volume.volumeId }}吗？
          </n-popconfirm>
        </n-space>
      </n-space>

      <n-a :href="file.url" :download="file.filename" target="_blank">
        <n-button>
          <template #icon>
            <n-icon :component="FileDownloadFilled" />
          </template>
          下载
        </n-button>
      </n-a>
    </n-space>

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
