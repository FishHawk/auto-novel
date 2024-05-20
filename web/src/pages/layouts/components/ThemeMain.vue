<script lang="ts" setup>
import { lightTheme, darkTheme, dateZhCN, useOsTheme, zhCN } from 'naive-ui';
import { Locator } from '@/data';

const osThemeRef = useOsTheme();

const { setting } = Locator.settingRepository();

const theme = computed(() => {
  const theme = setting.value.theme;
  let specificTheme: 'light' | 'dark' = 'light';
  if (theme !== 'system') {
    specificTheme = theme;
  } else if (osThemeRef.value) {
    specificTheme = osThemeRef.value;
  }
  return specificTheme === 'light' ? lightTheme : darkTheme;
});

watch(
  theme,
  (theme) => {
    const bodyColor = theme.common.bodyColor;
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
    :theme="theme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="{
      Drawer: { bodyPadding: '0px' },
      List: { color: '#0000' },
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
