import { useLocalStorage } from '@vueuse/core';

import { Setting } from '@/model/Setting';

export const createSettingRepository = () => {
  const setting = useLocalStorage<Setting>(
    'setting',
    {
      theme: 'light',
      enabledTranslator: ['baidu', 'youdao', 'gpt', 'sakura'],
      tocSortReverse: false,
      tocCollapseInNarrowScreen: true,
      hideCommmentWebNovel: false,
      hideCommmentWenkuNovel: false,
      hideLocalVolumeListInWorkspace: false,
      downloadFilenameType: 'zh',
      downloadFormat: {
        mode: 'zh-jp',
        translationsMode: 'priority',
        translations: ['sakura', 'gpt', 'youdao', 'baidu'],
        type: 'epub',
      },
      workspaceSound: false,
      paginationMode: 'auto',
    },
    { mergeDefaults: true },
  );

  const migrate = () => {
    const settingValue = setting.value;
    if ((settingValue as any).isDark !== undefined) {
      if ((settingValue as any).isDark === true) {
        settingValue.theme = 'dark';
      }
      (settingValue as any).isDark = undefined;
    }
    if (settingValue.enabledTranslator === undefined) {
      settingValue.enabledTranslator = ['baidu', 'youdao', 'gpt', 'sakura'];
    }
    if ((settingValue.downloadFormat.mode as any) === 'mix') {
      settingValue.downloadFormat.mode = 'zh-jp';
    } else if ((settingValue.downloadFormat.mode as any) === 'mix-reverse') {
      settingValue.downloadFormat.mode = 'jp-zh';
    } else if ((settingValue.downloadFormat.mode as any) === 'jp') {
      settingValue.downloadFormat.mode = 'zh';
    }
    // 2024-03-05
    if (settingValue.workspaceSound === undefined) {
      settingValue.workspaceSound = false;
    }
  };

  migrate();

  return {
    setting,
  };
};
