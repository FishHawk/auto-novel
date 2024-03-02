<script lang="ts" setup>
import {
  NConfigProvider,
  darkTheme,
  dateZhCN,
  lightTheme,
  zhCN,
  useOsTheme,
  GlobalThemeOverrides,
} from 'naive-ui';

import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { computed } from 'vue';
import { isDarkColor } from '@/pages/reader/components/util';

const readerSetting = useReaderSettingStore();
const osThemeRef = useOsTheme();

const readerTheme = computed(() => {
  let theme = lightTheme;
  let themeOverride: GlobalThemeOverrides = {
    common: {
      scrollbarWidth: '6px',
    },
  };

  if (readerSetting.theme.mode === 'light') {
    theme = lightTheme;
  } else if (readerSetting.theme.mode === 'dark') {
    theme = darkTheme;
  } else if (readerSetting.theme.mode === 'system') {
    if (osThemeRef.value) {
      theme = osThemeRef.value === 'dark' ? darkTheme : lightTheme;
    }
  } else {
    theme = isDarkColor(readerSetting.theme.bodyColor) ? darkTheme : lightTheme;
    themeOverride.common!.bodyColor = readerSetting.theme.bodyColor;
  }

  return { theme, themeOverride };
});
</script>

<template>
  <n-config-provider
    :theme="readerTheme.theme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="readerTheme.themeOverride"
  >
    <n-message-provider container-style="white-space: pre-wrap">
      <n-global-style />
      <n-layout
        :native-scrollbar="false"
        :scrollbar-props="{ trigger: 'none' }"
        style="height: 100vh"
      >
        <router-view :key="$route.path" />
      </n-layout>
    </n-message-provider>
  </n-config-provider>
</template>
