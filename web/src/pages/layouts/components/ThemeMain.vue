<script lang="ts" setup>
import { darkTheme, dateZhCN, useOsTheme, zhCN } from 'naive-ui';
import { computed } from 'vue';

import { useSettingStore } from '@/data/stores/setting';

const setting = useSettingStore();

const osThemeRef = useOsTheme();
const theme = computed(() => {
  let specificTheme: 'light' | 'dark' = 'light';
  if (setting.theme !== 'system') {
    specificTheme = setting.theme;
  } else if (osThemeRef.value) {
    specificTheme = osThemeRef.value;
  }
  return specificTheme === 'light' ? null : darkTheme;
});
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
      <slot />
    </n-message-provider>
  </n-config-provider>
</template>
