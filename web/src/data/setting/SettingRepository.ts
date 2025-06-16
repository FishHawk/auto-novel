import { useLocalStorage } from '@vueuse/core';

import { ReaderSetting, Setting } from '@/data/setting/Setting';
import { CCUtil } from '@/util/cc';

export const createSettingRepository = () => {
  const setting = useLocalStorage<Setting>('setting', Setting.defaultValue, {
    mergeDefaults: true,
  });
  Setting.migrate(setting.value);

  const cc = ref(CCUtil.defaultConverter);

  const activateCC = () => {
    watch(
      () => setting.value.locale,
      async (locale) => {
        cc.value = await CCUtil.createConverter(locale);
      },
      { immediate: true },
    );
  };

  return { setting, cc, activateCC };
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
