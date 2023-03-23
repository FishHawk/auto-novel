import { defineStore } from 'pinia';

export const themeOptions = [
  { isDark: false, bodyColor: '#FFFFFF' },
  { isDark: false, bodyColor: '#FFF2E2' },
  { isDark: false, bodyColor: '#E3EDCD' },
  { isDark: false, bodyColor: '#E9EBFE' },
  { isDark: false, bodyColor: '#EAEAEF' },

  { isDark: true, bodyColor: '#000000' },
  { isDark: true, bodyColor: '#272727' },
];

export const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中日混合' },
];

export const fontSizeOptions = ['14px', '16px', '18px', '20px'];

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
