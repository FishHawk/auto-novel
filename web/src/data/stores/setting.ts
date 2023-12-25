import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator/translator';

export interface Setting {
  isDark: boolean;
  tocSortReverse: boolean;
  downloadFilenameType: 'jp' | 'zh';

  isDownloadFormatSameAsReaderFormat: boolean;
  downloadFormat: {
    mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
    translationsMode: 'parallel' | 'priority';
    translations: TranslatorId[];
    type: 'epub' | 'txt';
  };
}

export const useSettingStore = defineStore('setting', {
  state: () =>
    <Setting>{
      isDark: false,
      tocSortReverse: false,
      downloadFilenameType: 'zh',
      isDownloadFormatSameAsReaderFormat: true,
      downloadFormat: {
        mode: 'mix',
        translationsMode: 'priority',
        translations: ['sakura', 'gpt', 'youdao', 'baidu'],
        type: 'epub',
      },
    },
  persist: true,
});
