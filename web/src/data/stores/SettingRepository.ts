import { useLocalStorage } from '@vueuse/core';

import { ReaderSetting, Setting } from '@/model/Setting';

export const createSettingRepository = () => {
  const setting = useLocalStorage<Setting>('setting', Setting.defaultValue, {
    mergeDefaults: true,
  });
  Setting.migrate(setting.value);
  return { setting };
};

export const createReaderSettingRepository = () => {
  const setting = useLocalStorage<ReaderSetting>(
    'readerSetting',
    ReaderSetting.defaultValue,
    { mergeDefaults: true },
  );
  ReaderSetting.migrate(setting.value);
  return { setting };
};
