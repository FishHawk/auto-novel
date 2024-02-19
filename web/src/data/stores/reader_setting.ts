import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
  translationsMode: 'parallel' | 'priority';
  translations: TranslatorId[];
  fontSize: string;
  theme: { isDark: boolean; bodyColor: string; fontColor?: string };
  enableSakuraReportButton: boolean;
  enableExtraLineSpacing: boolean;
  mixJpOpacity: number;
  mixZhOpacity: number;
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      mode: 'mix',
      translationsMode: 'priority',
      translations: ['sakura', 'gpt', 'youdao', 'baidu'],
      fontSize: '14px',
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      enableSakuraReportButton: true,
      enableExtraLineSpacing: true,
      mixJpOpacity: 0.4,
      mixZhOpacity: 0.75,
    },
  persist: true,
});
