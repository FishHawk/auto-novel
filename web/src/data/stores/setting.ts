import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';

export interface Setting {
  theme: 'light' | 'dark' | 'system';
  enabledTranslator: TranslatorId[];
  tocSortReverse: boolean;
  downloadFilenameType: 'jp' | 'zh';
  downloadFormat: {
    mode: 'zh' | 'zh-jp' | 'jp-zh';
    translationsMode: 'parallel' | 'priority';
    translations: TranslatorId[];
    type: 'epub' | 'txt';
  };
}

export const useSettingStore = defineStore('setting', {
  state: () =>
    <Setting>{
      theme: 'light',
      enabledTranslator: ['baidu', 'youdao', 'gpt', 'sakura'],
      tocSortReverse: false,
      downloadFilenameType: 'zh',
      downloadFormat: {
        mode: 'zh-jp',
        translationsMode: 'priority',
        translations: ['sakura', 'gpt', 'youdao', 'baidu'],
        type: 'epub',
      },
    },
  persist: true,
});

export const downloadModeOptions = [
  { value: 'zh', label: '中文' },
  { value: 'zh-jp', label: '中日' },
  { value: 'jp-zh', label: '日中' },
];
export const downloadTranslationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
export const downloadTypeOptions = [
  { value: 'epub', label: 'EPUB' },
  { value: 'txt', label: 'TXT' },
];

export const migrateSetting = (setting: ReturnType<typeof useSettingStore>) => {
  if ((setting as any).isDark !== undefined) {
    if ((setting as any).isDark === true) {
      setting.theme = 'dark';
    }
    (setting as any).isDark = undefined;
  }
  if (setting.enabledTranslator === undefined) {
    setting.enabledTranslator = ['baidu', 'youdao', 'gpt', 'sakura'];
  }
  if ((setting.downloadFormat.mode as any) === 'mix') {
    setting.downloadFormat.mode = 'zh-jp';
  } else if ((setting.downloadFormat.mode as any) === 'mix-reverse') {
    setting.downloadFormat.mode = 'jp-zh';
  } else if ((setting.downloadFormat.mode as any) === 'jp') {
    setting.downloadFormat.mode = 'zh';
  }
};
