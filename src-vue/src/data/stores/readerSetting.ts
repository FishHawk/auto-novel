import { defineStore } from 'pinia';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix' | 'mix-reverse';
  mixJpOpacity: number;

  translation: 'youdao' | 'baidu';
  fontSize: '14px' | '16px' | '18px' | '20px';
  theme: { isDark: boolean; bodyColor: string };
  debugCompare: boolean;
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      mode: 'mix',
      mixJpOpacity: 0.4,

      translation: 'youdao',
      fontSize: '14px',
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      debugCompare: false,
    },
  persist: true,
});
