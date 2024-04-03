import { useLocalStorage } from '@vueuse/core';

import { Setting } from '@/model/Setting';

export const createSettingRepository = () => {
  const ref = useLocalStorage<Setting>('setting', {
    theme: 'light',
    enabledTranslator: ['baidu', 'youdao', 'gpt', 'sakura'],
    tocSortReverse: false,
    downloadFilenameType: 'zh',
    downloadFormat: {
      mode: 'zh-jp',
      translationsMode: 'priority',
      translations: ['sakura', 'gpt', 'youdao', 'baidu'],
      type: 'epub',
    },
    workspaceSound: false,
  });

  const migrate = () => {
    const setting = ref.value;
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
  };

  migrate();

  return {
    ref,
  };
};
