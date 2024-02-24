import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
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
      mode: 'mix',
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
