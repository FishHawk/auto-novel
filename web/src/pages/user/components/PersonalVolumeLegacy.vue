<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { computed } from 'vue';

import { ApiUserPersonal, PersonalVolume } from '@/data/api/api_user_personal';
import { useSettingStore } from '@/data/stores/setting';
import { TranslatorId } from '@/data/translator/translator';

const { volume, downloadToken } = defineProps<{
  volume: PersonalVolume;
  downloadToken: string;
}>();

const setting = useSettingStore();

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
  const { mode, translationsMode, translations } = setting.downloadFormat;

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
</script>

<template>
  <div>
    <n-space align="center" justify="space-between" :wrap="false">
      <n-space vertical>
        <n-text>{{ volume.volumeId }}</n-text>
        <n-space>
          <n-text v-for="{ label } of translatorLabels" type="primary">
            {{ label }}
          </n-text>
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
  </div>
</template>
