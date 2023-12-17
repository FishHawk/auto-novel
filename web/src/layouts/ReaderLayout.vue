<script lang="ts" setup>
import { NConfigProvider, darkTheme, dateZhCN, lightTheme, zhCN } from 'naive-ui';

import { useReaderSettingStore } from '@/data/stores/reader_setting';

const readerSetting = useReaderSettingStore();
</script>

<template>
  <n-config-provider
    :theme="readerSetting.theme.isDark ? darkTheme : lightTheme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="{
      common: { bodyColor: readerSetting.theme.bodyColor },
    }"
  >
    <n-message-provider container-style="white-space: pre-wrap">
      <n-global-style />
      <router-view :key="$route.path" />
    </n-message-provider>
  </n-config-provider>
</template>
