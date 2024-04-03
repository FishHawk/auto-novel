<script lang="ts" setup>
import { darkTheme, dateZhCN, zhCN, useOsTheme } from 'naive-ui';

import { ReaderSettingRepository } from '@/data/stores';
import { isDarkColor } from '@/pages/util';

const osThemeRef = useOsTheme();

const setting = ReaderSettingRepository.ref();
const readerTheme = computed(() => {
  const readerTheme = setting.value.theme;

  let theme: typeof darkTheme | null = null;
  let bodyColor: string | undefined = undefined;

  if (readerTheme.mode === 'light') {
    theme = null;
  } else if (readerTheme.mode === 'dark') {
    theme = darkTheme;
  } else if (readerTheme.mode === 'system') {
    if (osThemeRef.value) {
      theme = osThemeRef.value === 'dark' ? darkTheme : null;
    }
  } else if (readerTheme.mode === 'custom') {
    theme = isDarkColor(readerTheme.bodyColor) ? darkTheme : null;
    bodyColor = readerTheme.bodyColor;
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
        ...readerTheme.bodyColor,
      },
    }"
  >
    <n-global-style />
    <n-message-provider container-style="white-space: pre-wrap">
      <n-loading-bar-provider>
        <slot />
      </n-loading-bar-provider>
    </n-message-provider>
  </n-config-provider>
</template>
