import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';

export interface Setting {
  theme: 'light' | 'dark' | 'system';
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
      theme: 'light',
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
