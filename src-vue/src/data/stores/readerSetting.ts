import { defineStore } from 'pinia';

export interface ReaderSetting {
  theme: { isDark: boolean; bodyColor: string };
  mode: 'jp' | 'zh' | 'mix';
  fontSize: '14px' | '16px' | '18px' | '20px';
}

export const useReaderSettingStore = defineStore('readerSetting', {
  state: () =>
    <ReaderSetting>{
      theme: { isDark: false, bodyColor: '#FFFFFF' },
      mode: 'mix',
      fontSize: '14px',
    },
  persist: true,
});
