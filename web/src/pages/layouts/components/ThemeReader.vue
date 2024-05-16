<script lang="ts" setup>
import { lightTheme, darkTheme, dateZhCN, zhCN, useOsTheme } from 'naive-ui';

import { Locator } from '@/data';
import { isDarkColor } from '@/pages/util';
import type { BuiltInGlobalTheme } from 'naive-ui/es/themes/interface';

const osThemeRef = useOsTheme();

const { setting } = Locator.readerSettingRepository();
const readerTheme = computed(() => {
  const readerTheme = setting.value.theme;

  let theme: BuiltInGlobalTheme = lightTheme;
  let bodyColor: string | undefined = undefined;

  if (readerTheme.mode === 'light') {
    theme = lightTheme;
  } else if (readerTheme.mode === 'dark') {
    theme = darkTheme;
  } else if (readerTheme.mode === 'system') {
    if (osThemeRef.value) {
      theme = osThemeRef.value === 'dark' ? darkTheme : lightTheme;
    }
  } else if (readerTheme.mode === 'custom') {
    theme = isDarkColor(readerTheme.bodyColor) ? darkTheme : lightTheme;
    bodyColor = readerTheme.bodyColor;
  }

  return { theme, bodyColor: bodyColor ? { bodyColor } : undefined };
});

watch(
  readerTheme,
  (readerTheme) => {
    const bodyColor =
      readerTheme.bodyColor?.bodyColor ?? readerTheme.theme.common.bodyColor;
    if (bodyColor) {
      const meta = document.querySelector('meta[name="theme-color"]')!;
      meta.setAttribute('content', bodyColor);
    }
  },
  { immediate: true },
);
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
