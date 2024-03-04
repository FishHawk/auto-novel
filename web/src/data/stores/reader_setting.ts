import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';
import { isDarkColor } from '@/pages/reader/components/util';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: TranslatorId[];
  fontSize: number;
  lineSpace: number;
  theme: {
    mode: 'light' | 'dark' | 'system' | 'custom';
    bodyColor: string;
    fontColor: string;
  };
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
      theme: { mode: 'light', bodyColor: '#FFFFFF', fontColor: '#000000' },
      enableSakuraReportButton: true,
      mixJpOpacity: 0.4,
      mixZhOpacity: 0.75,
    },
  persist: true,
});

export const modeOptions = [
  { label: '日文', value: 'jp' },
  { label: '中文', value: 'zh' },
  { label: '中日', value: 'zh-jp' },
  { label: '日中', value: 'jp-zh' },
];
export const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];

export const fontSizeOptions = [14, 16, 18, 20, 24, 30, 40];

export const lineSpaceOptions = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0];

export const themeModeOptions = [
  { label: '浅色', value: 'light' },
  { label: '深色', value: 'dark' },
  { label: '跟随系统', value: 'system' },
  { label: '自定义', value: 'custom' },
];
export const themeOptions = [
  { bodyColor: '#FFFFFF', fontColor: '#000000' },
  { bodyColor: '#FFF2E2', fontColor: '#000000' },
  { bodyColor: '#E3EDCD', fontColor: '#000000' },
  { bodyColor: '#E9EBFE', fontColor: '#000000' },
  { bodyColor: '#EAEAEF', fontColor: '#000000' },

  { bodyColor: '#000000', fontColor: '#FFFFFF' },
  { bodyColor: '#272727', fontColor: '#FFFFFF' },
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
  const theme = setting.theme as any;
  if (theme.isDark !== undefined) {
    if (theme.bodyColor === '#FFFFFF' && theme.fontColor === undefined) {
      setting.theme = {
        mode: 'light',
        bodyColor: '#FFFFFF',
        fontColor: '#000000',
      };
    } else if (theme.bodyColor === '#272727' && theme.fontColor === undefined) {
      setting.theme = {
        mode: 'dark',
        bodyColor: '#FFFFFF',
        fontColor: '#000000',
      };
    } else {
      setting.theme = {
        mode: 'custom',
        bodyColor: theme.bodyColor,
        fontColor: isDarkColor(theme.bodyColor) ? '#FFFFFF' : '#000000',
      };
    }
  }
};
