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

function toggleDownloadOptions() {
  showDownloadOptions.value = !showDownloadOptions.value;
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
    旧版文件翻译正在逐步废弃，请使用新的本地版
    <router-n-a to="/personal">文件翻译</router-n-a>
    。
  </n-p>

  <workspace-nav />

  <n-button @click="toggleDownloadOptions()" style="margin-bottom: 8px">
    下载设置
  </n-button>

  <ResultView
    :result="volumesResult"
    :showEmpty="(it: any) => it.length === 0"
    v-slot="{ value: volumes }"
  >
    <n-collapse-transition
      :show="showDownloadOptions"
      style="margin-bottom: 16px"
    >
      <n-list bordered>
        <n-list-item>
          <AdvanceOptionRadio
            title="自定义下载文件语言"
            description="设置下载文件的语言。注意部分Epub阅读器不支持自定义字体颜色，日文段落会被强制使用黑色字体。"
            v-model:value="setting.downloadFormat.mode"
            :options="modeOptions"
          />
        </n-list-item>

        <n-list-item>
          <AdvanceOptionRadio
            title="自定义下载文件翻译"
            description="设置下载文件使用的翻译。注意右侧选中的翻译的顺序，优先模式顺序代表优先级，并列模式顺序代表翻译的排列顺序。"
            v-model:value="setting.downloadFormat.translationsMode"
            :options="translationModeOptions"
          >
            <n-transfer
              v-model:value="setting.downloadFormat.translations"
              :options="translationOptions"
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
          />
        </n-list-item>
      </template>
    </n-list>
  </ResultView>
</template>
