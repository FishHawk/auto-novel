import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: TranslatorId[];
  fontSize: number;
  lineSpace: number;
  theme: { isDark: boolean; bodyColor: string; fontColor?: string };
  enableSakuraReportButton: boolean;
  mixJpOpacity: number;
  mixZhOpacity: number;
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      mode: 'zh-jp',
      translationsMode: 'priority',
      translations: ['sakura', 'gpt', 'youdao', 'baidu'],
      fontSize: 14,
      lineSpace: 1.0,
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      enableSakuraReportButton: true,
      mixJpOpacity: 0.4,
      mixZhOpacity: 0.75,
    },
  persist: true,
});

export const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'zh-jp', label: '中日' },
  { value: 'jp-zh', label: '日中' },
];

export const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];

export const fontSizeOptions = [14, 16, 18, 20, 24, 30, 40];

export const lineSpaceOptions = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0];

export const themeOptions = [
  { isDark: false, bodyColor: '#FFFFFF' },
  { isDark: false, bodyColor: '#FFF2E2' },
  { isDark: false, bodyColor: '#E3EDCD' },
  { isDark: false, bodyColor: '#E9EBFE' },
  { isDark: false, bodyColor: '#EAEAEF' },

  { isDark: true, bodyColor: '#000000' },
  { isDark: true, bodyColor: '#272727' },
];

export const migrateReaderSetting = (
  setting: ReturnType<typeof useReaderSettingStore>
) => {
  if (typeof setting.fontSize === 'string') {
    setting.fontSize = Number((setting.fontSize as any).replace(/[^0-9]/g, ''));
  }
  if ((setting.mode as any) === 'mix') {
    setting.mode = 'zh-jp';
  } else if ((setting.mode as any) === 'mix-reverse') {
    setting.mode = 'jp-zh';
  }
};
