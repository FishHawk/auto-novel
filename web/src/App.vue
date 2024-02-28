<script lang="ts" setup>
import { watch } from 'vue';

import { ApiAuth } from '@/data/api/api_auth';
import { updateToken } from '@/data/api/client';
import {
  migrateReaderSetting,
  useReaderSettingStore,
} from '@/data/stores/reader_setting';
import { migrateSetting, useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();

// 全局注册token
watch(
  () => userData.token,
  (newToken, _oldToken) => updateToken(newToken),
  { immediate: true }
);

// 每隔24小时刷新登录状态
if (userData.isLoggedIn) {
  const sinceLoggedIn = Date.now() - (userData.renewedAt ?? 0);
  if (!userData.info?.createAt || sinceLoggedIn > 24 * 3600 * 1000) {
    ApiAuth.renew().then((result) => {
      if (result.ok) {
        userData.setProfile(result.value);
      }
    });
  }
}

// 设置格式迁移
const setting = useSettingStore();
migrateSetting(setting);

const readerSetting = useReaderSettingStore();
migrateReaderSetting(readerSetting);
</script>

<template>
  <router-view :key="$route.path" />
</template>

<style>
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
</style>
