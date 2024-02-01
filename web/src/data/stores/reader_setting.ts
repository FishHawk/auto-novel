import { defineStore } from 'pinia';

import { TranslatorId } from '@/data/translator/translator';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
  translationsMode: 'parallel' | 'priority';
  translations: TranslatorId[];
  fontSize: '14px' | '16px' | '18px' | '20px';
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
      fontSize: '14px',
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      enableSakuraReportButton: true,
      mixJpOpacity: 0.4,
      mixZhOpacity: 0.75,
    },
  persist: true,
});
