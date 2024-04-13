import { useLocalStorage } from '@vueuse/core';

import { ReaderSetting } from '@/model/Setting';
import { isDarkColor } from '@/pages/util';

export const createReaderSettingRepository = () => {
  const ref = useLocalStorage<ReaderSetting>(
    'readerSetting',
    {
      mode: 'zh-jp',
      translationsMode: 'priority',
      translations: ['sakura', 'gpt', 'youdao', 'baidu'],
      fontSize: 14,
      lineSpace: 1.0,
      theme: { mode: 'light', bodyColor: '#FFFFFF', fontColor: '#000000' },
      speakLanguages: ['jp'],
      enableSakuraReportButton: true,
      mixJpOpacity: 0.4,
      mixZhOpacity: 0.75,
    },
    { mergeDefaults: true }
  );

  const migrate = () => {
    const setting = ref.value;
    if (typeof setting.fontSize === 'string') {
      setting.fontSize = Number(
        (setting.fontSize as any).replace(/[^0-9]/g, '')
      );
    }
    if ((setting.mode as any) === 'mix') {
      setting.mode = 'zh-jp';
    } else if ((setting.mode as any) === 'mix-reverse') {
      setting.mode = 'jp-zh';
    }
    const theme = setting.theme as any;
    if (theme.isDark !== undefined) {
      if (theme.bodyColor === '#FFFFFF' && theme.fontColor === undefined) {
        setting.theme = {
          mode: 'light',
          bodyColor: '#FFFFFF',
          fontColor: '#000000',
        };
      } else if (
        theme.bodyColor === '#272727' &&
        theme.fontColor === undefined
      ) {
        setting.theme = {
          mode: 'dark',
          bodyColor: '#FFFFFF',
          fontColor: '#000000',
        };
      } else {
        setting.theme = {
          mode: 'custom',
          bodyColor: theme.bodyColor,
          fontColor: isDarkColor(theme.bodyColor) ? '#FFFFFF' : '#000000',
        };
      }
    }
  };

  migrate();

  return {
    ref,
  };
};
