import { defineStore } from 'pinia';
import { TranslatorId } from '../translator/translator';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
  translationsMode: 'parallel' | 'priority';
  translations: TranslatorId[];
  fontSize: '14px' | '16px' | '18px' | '20px';
  theme: { isDark: boolean; bodyColor: string };
  mixJpOpacity: number;
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      mode: 'mix',
      translationsMode: 'priority',
      translations: ['gpt', 'youdao', 'baidu'],
      fontSize: '14px',
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      mixJpOpacity: 0.4,
    },
  persist: true,
});
