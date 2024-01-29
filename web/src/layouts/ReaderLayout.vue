<script lang="ts" setup>
import {
  NConfigProvider,
  darkTheme,
  dateZhCN,
  lightTheme,
  zhCN,
} from 'naive-ui';

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
      common: {
        bodyColor: readerSetting.theme.bodyColor,
        scrollbarWidth: '6px',
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
