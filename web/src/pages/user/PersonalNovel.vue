<script lang="ts" setup>
import { ref } from 'vue';

import type {
  PersonalVolume,
  PersonalVolumes,
} from '@/data/api/api_user_personal';
import { ApiUserPersonal } from '@/data/api/api_user_personal';
import { ResultState } from '@/data/result';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();
const setting = useSettingStore();

const volumesResult = ref<ResultState<PersonalVolumes>>();

async function loadVolume() {
  if (userData.isLoggedIn) {
    const result = await ApiUserPersonal.listVolume();
    volumesResult.value = result;
  }
}
loadVolume();

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

function sortVolumesJp(volumes: PersonalVolume[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}
</script>

<template>
  <n-p>
    旧版文件翻译之后会逐步废弃，目前已经关闭上传功能，请使用新的本地版
    <router-n-a to="/personal">文件翻译</router-n-a>
    。
  </n-p>

  <workspace-nav />

  <n-button-group style="margin-bottom: 8px">
    <n-button @click="toggleTranslateOptions()"> 翻译设置 </n-button>
    <n-button @click="toggleDownloadOptions()">下载设置</n-button>
  </n-button-group>

  <ResultView
    :result="volumesResult"
    :showEmpty="(it: any) => it.length === 0"
    v-slot="{ value: volumes }"
  >
    <n-collapse-transition
      :show="showTranslateOptions || showDownloadOptions"
      style="margin-bottom: 16px"
    >
      <n-list v-if="showTranslateOptions" bordered>
        <n-list-item>
          <AdvanceOptionSwitch
            title="翻译过期章节"
            description="在启动翻译任务时，重新翻译术语表过期的章节。一次性设定，默认关闭。"
            v-model:value="translateExpireChapter"
          />
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
              style="height: 190px; margin-top: 8px; font-size: 12px"
            />
          </AdvanceOptionRadio>
        </n-list-item>
      </n-list>
    </n-collapse-transition>

    <n-list>
      <template v-for="volume of sortVolumesJp(volumes.volumes)">
        <n-list-item>
          <personal-volume-legacy
            :volume="volume"
            :download-token="volumes.downloadToken"
            :get-params="() => ({ translateExpireChapter })"
          />
        </n-list-item>
      </template>
    </n-list>
  </ResultView>
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
