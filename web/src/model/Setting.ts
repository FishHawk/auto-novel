import { TranslatorId } from "./Translator";

export interface Setting {
  theme: 'light' | 'dark' | 'system';
  enabledTranslator: TranslatorId[];
  tocSortReverse: boolean;
  downloadFilenameType: 'jp' | 'zh';
  downloadFormat: {
    mode: 'zh' | 'zh-jp' | 'jp-zh';
    translationsMode: 'parallel' | 'priority';
    translations: TranslatorId[];
    type: 'epub' | 'txt';
  };
  workspaceSound: boolean;
}

export namespace Setting {
  export const downloadModeOptions = [
    { label: '中文', value: 'zh' },
    { label: '中日', value: 'zh-jp' },
    { label: '日中', value: 'jp-zh' },
  ];
  export const downloadTranslationModeOptions = [
    { label: '优先', value: 'priority' },
    { label: '并列', value: 'parallel' },
  ];
  export const downloadTypeOptions = [
    { label: 'EPUB', value: 'epub' },
    { label: 'TXT', value: 'txt' },
  ];

  export const themeOptions = [
    { label: '亮色主题', value: 'light' },
    { label: '暗色主题', value: 'dark' },
    { label: '跟随系统', value: 'system' },
  ];
}

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

export namespace ReaderSetting {
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
}