import { defineStore } from 'pinia';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
  translation: 'youdao' | 'baidu' | 'youdao/baidu' | 'gpt';
  fontSize: '14px' | '16px' | '18px' | '20px';
  theme: { isDark: boolean; bodyColor: string };
  mixJpOpacity: number;
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      mode: 'mix',
      translation: 'youdao',
      fontSize: '14px',
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      mixJpOpacity: 0.4,
    },
  persist: true,
});
