import { defineStore } from 'pinia';

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'mix';
  translation: 'youdao' | 'baidu' | 'compare';
  fontSize: '14px' | '16px' | '18px' | '20px';
  theme: { isDark: boolean; bodyColor: string };
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      mode: 'mix',
      translation: 'youdao',
      fontSize: '14px',
      theme: { isDark: false, bodyColor: '#FFFFFF' },
    },
  persist: true,
});
