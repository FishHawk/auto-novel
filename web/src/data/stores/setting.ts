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
  workspaceSound: boolean;
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
      workspaceSound: false,
    },
  persist: true,
});

export const downloadModeOptions = [
  { label: '中文', value: 'zh' },
  { label: '中日', value: 'zh-jp' },
  { label: '日中', value: 'jp-zh' },
];
export const downloadTranslationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
export const downloadTypeOptions = [
  { label: 'EPUB', value: 'epub' },
  { label: 'TXT', value: 'txt' },
];

export const themeOptions = [
  { label: '亮色主题', value: 'light' },
  { label: '暗色主题', value: 'dark' },
  { label: '跟随系统', value: 'system' },
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
  // 2024-03-05
  if (setting.workspaceSound === undefined) {
    setting.workspaceSound = false;
  }
};
