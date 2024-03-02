<script lang="ts" setup>
import {
  NConfigProvider,
  darkTheme,
  dateZhCN,
  lightTheme,
  zhCN,
  useOsTheme,
} from 'naive-ui';

import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { computed } from 'vue';
import { isDarkColor } from '@/pages/reader/components/util';

const readerSetting = useReaderSettingStore();
const osThemeRef = useOsTheme();

const readerTheme = computed(() => {
  let theme = lightTheme;
  let bodyColor = undefined;

  if (readerSetting.theme.mode === 'light') {
    theme = lightTheme;
  } else if (readerSetting.theme.mode === 'dark') {
    theme = darkTheme;
  } else if (readerSetting.theme.mode === 'system') {
    if (osThemeRef.value) {
      theme = osThemeRef.value === 'dark' ? darkTheme : lightTheme;
    }
  } else if (readerSetting.theme.mode === 'custom') {
    theme = isDarkColor(readerSetting.theme.bodyColor) ? darkTheme : lightTheme;
    bodyColor = readerSetting.theme.bodyColor;
  }

  return { theme, bodyColor: bodyColor ? { bodyColor } : undefined };
});
</script>

<template>
  <n-config-provider
    :theme="readerTheme.theme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="{
      common: {
        scrollbarWidth: '6px',
        ...readerTheme.bodyColor,
      },
    }"
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
