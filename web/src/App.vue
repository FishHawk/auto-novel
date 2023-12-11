<script lang="ts" setup>
import { darkTheme, dateZhCN, lightTheme, zhCN } from 'naive-ui';
import { watch } from 'vue';

import { ApiAuth } from './data/api/api_auth';
import { updateToken } from './data/api/client';
import { useSettingStore } from './data/stores/setting';
import { useUserDataStore } from './data/stores/user_data';

const setting = useSettingStore();
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
</script>

<template>
  <n-config-provider
    :theme="setting.isDark ? darkTheme : lightTheme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
  >
    <n-message-provider container-style="white-space: pre-wrap">
      <router-view :key="$route.path" />
    </n-message-provider>
  </n-config-provider>
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
</style>
