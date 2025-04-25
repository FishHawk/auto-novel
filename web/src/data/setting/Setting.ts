import { TranslatorId } from '../../model/Translator';

export interface Setting {
  theme: 'light' | 'dark' | 'system';
  enabledTranslator: TranslatorId[];
  tocSortReverse: boolean;
  //
  tocCollapseInNarrowScreen: boolean;
  tocExpandAll: boolean;
  hideCommmentWebNovel: boolean;
  hideCommmentWenkuNovel: boolean;
  showTagInWebFavored: boolean;
  favoriteCreateTimeFirst: boolean;
  //
  autoTopJobWhenAddTask: boolean;
  //
  menuCollapsed: boolean;
  //
  downloadFilenameType: 'jp' | 'zh';
  downloadFormat: {
    mode: 'zh' | 'zh-jp' | 'jp-zh';
    translationsMode: 'parallel' | 'priority';
    translations: TranslatorId[];
    type: 'epub' | 'txt';
  };
  workspaceSound: boolean;
  paginationMode: 'pagination' | 'scroll';
  localVolumeOrder: {
    value: 'byCreateAt' | 'byReadAt' | 'byId';
    desc: boolean;
  };
  //
  locale: 'zh-cn' | 'zh-tw';
  searchLocaleAware: boolean;
}

export namespace Setting {
  export const defaultValue: Setting = {
    theme: 'system',
    enabledTranslator: ['baidu', 'youdao', 'gpt', 'sakura'],
    tocSortReverse: false,
    //
    tocCollapseInNarrowScreen: true,
    tocExpandAll: true,
    hideCommmentWebNovel: false,
    hideCommmentWenkuNovel: false,
    showTagInWebFavored: false,
    favoriteCreateTimeFirst: false,
    //
    autoTopJobWhenAddTask: false,
    //
    menuCollapsed: false,
    //
    downloadFilenameType: 'zh',
    downloadFormat: {
      mode: 'zh-jp',
      translationsMode: 'priority',
      translations: ['sakura', 'gpt', 'youdao', 'baidu'],
      type: 'epub',
    },
    workspaceSound: false,
    paginationMode: 'pagination',
    localVolumeOrder: {
      value: 'byCreateAt',
      desc: true,
    },
    //
    locale: 'zh-cn',
    searchLocaleAware: false,
  };

  export const migrate = (setting: Setting) => {
    if ((setting as any).isDark !== undefined) {
      if ((setting as any).isDark === true) {
        setting.theme = 'dark';
      }
      (setting as any).isDark = undefined;
    }
    if (setting.enabledTranslator === undefined) {
      setting.enabledTranslator = ['baidu', 'youdao', 'gpt', 'sakura'];
    }
    if ((setting.downloadFormat.mode as any) === 'mix') {
      setting.downloadFormat.mode = 'zh-jp';
    } else if ((setting.downloadFormat.mode as any) === 'mix-reverse') {
      setting.downloadFormat.mode = 'jp-zh';
    } else if ((setting.downloadFormat.mode as any) === 'jp') {
      setting.downloadFormat.mode = 'zh';
    }
    // 2024-03-05
    if (setting.workspaceSound === undefined) {
      setting.workspaceSound = false;
    }
    // 2024-05-28
    if ((setting.paginationMode as any) === 'auto') {
      setting.paginationMode = 'pagination';
    }
  };

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
  export const paginationModeOptions = [
    { label: '分页', value: 'pagination' },
    { label: '滚动', value: 'scroll' },
  ];
  export const localVolumeOrderOptions = [
    { value: 'byCreateAt', label: '添加时间' },
    { value: 'byReadAt', label: '阅读时间' },
    { value: 'byId', label: '标题' },
  ];
  export const localeOptions = [
    { label: '简体中文', value: 'zh-cn' },
    { label: '繁体中文', value: 'zh-tw' },
  ];
}

export interface ReaderSetting {
  mode: 'jp' | 'zh' | 'zh-jp' | 'jp-zh';
  translationsMode: 'parallel' | 'priority';
  translations: TranslatorId[];
  clickArea: 'default' | 'left-right' | 'up-down' | 'none';
  speakLanguages: string[];
  trimLeadingSpaces: boolean;
  enableSourceLabel: boolean;
  //
  fontWeight: number;
  fontSize: number;
  lineSpace: number;
  pageWidth: number;
  theme: {
    mode: 'light' | 'dark' | 'system' | 'custom';
    bodyColor: string;
    fontColor: string;
  };
  mixJpOpacity: number;
  mixZhOpacity: number;
}

export namespace ReaderSetting {
  export const defaultValue: ReaderSetting = {
    mode: 'zh-jp',
    translationsMode: 'priority',
    translations: ['sakura', 'gpt', 'youdao', 'baidu'],
    clickArea: 'default',
    speakLanguages: ['jp'],
    trimLeadingSpaces: false,
    enableSourceLabel: false,
    //
    fontWeight: 400,
    fontSize: 14,
    lineSpace: 1.0,
    pageWidth: 800,
    theme: {
      mode: 'system',
      bodyColor: '#FFFFFF',
      fontColor: '#000000',
    },
    mixJpOpacity: 0.4,
    mixZhOpacity: 0.75,
  };

  export const migrate = (setting: ReaderSetting) => {
    if (typeof setting.fontSize === 'string') {
      setting.fontSize = Number(
        (setting.fontSize as any).replace(/[^0-9]/g, ''),
      );
    }
    if ((setting.mode as any) === 'mix') {
      setting.mode = 'zh-jp';
    } else if ((setting.mode as any) === 'mix-reverse') {
      setting.mode = 'jp-zh';
    }
    const theme = setting.theme as any;
    if (theme.isDark !== undefined) {
      if (theme.bodyColor === '#272727' && theme.fontColor === undefined) {
        setting.theme = {
          mode: 'dark',
          bodyColor: '#FFFFFF',
          fontColor: '#000000',
        };
      } else {
        setting.theme = {
          mode: 'light',
          bodyColor: '#FFFFFF',
          fontColor: '#000000',
        };
      }
    }
  };

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

  export const clickAreaOptions = [
    { label: '默认', value: 'default' },
    { label: '左右', value: 'left-right' },
    { label: '上下', value: 'up-down' },
    { label: '关闭', value: 'none' },
  ];

  export const speakLanguagesOptions = [
    { label: '中文', value: 'zh' },
    { label: '日文', value: 'jp' },
  ];

  export const fontWeightOptions = [
    { label: '正常', value: 400 },
    { label: '加粗', value: 600 },
  ];

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
