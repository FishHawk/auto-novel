<script lang="ts" setup>
import {
  migrateReaderSetting,
  useReaderSettingStore,
} from '@/data/stores/reader_setting';
import { migrateSetting, useSettingStore } from '@/data/stores/setting';
import { AuthService } from '@/data/auth/AuthService';

AuthService.activate();

// 设置格式迁移
const setting = useSettingStore();
migrateSetting(setting);

const readerSetting = useReaderSettingStore();
migrateReaderSetting(readerSetting);
</script>

<template>
  <router-view />
</template>

<style>
body {
  overflow-y: scroll;
}
a {
  text-decoration: none;
}
p,
li {
  overflow-wrap: break-word;
  word-break: break-word;
}
.n-h:first-child {
  margin: var(--n-margin);
}
.text-2line {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.float {
  position: fixed;
  right: 40px;
  bottom: 20px;
  box-shadow: rgb(0 0 0 / 40%) 2px 2px 8px 0px;
}
.n-drawer-header__main {
  flex: 1;
}
</style>
