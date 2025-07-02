import { ReaderSetting, Setting } from '@/data/setting/Setting';
import { defaultConverter, useLocalStorage, useOpenCC } from '@/util';

import { LSKey } from '../LocalStorage';

export const createSettingRepository = () => {
  const setting = useLocalStorage<Setting>(LSKey.Setting, Setting.defaultValue);
  Setting.migrate(setting.value);

  const cc = ref(defaultConverter);

  const activateCC = () => {
    watch(
      () => setting.value.locale,
      async (locale) => {
        cc.value = await useOpenCC(locale);
      },
      { immediate: true },
    );
  };

  return { setting, cc, activateCC };
};

export const createReaderSettingRepository = () => {
  const setting = useLocalStorage<ReaderSetting>(
    LSKey.SettingReader,
    ReaderSetting.defaultValue,
  );
  ReaderSetting.migrate(setting.value);
  return { setting };
};
