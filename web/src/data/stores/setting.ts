import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';

export interface Setting {
  isDark: boolean;
  tocSortReverse: boolean;
  downloadFilenameType: 'jp' | 'zh';
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
      downloadFormat: {
        mode: 'mix',
        translationsMode: 'priority',
        translations: ['sakura', 'gpt', 'youdao', 'baidu'],
        type: 'epub',
      },
    },
  persist: true,
});
