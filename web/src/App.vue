<script lang="ts" setup>
import { watch } from 'vue';

import { ApiAuth } from '@/data/api/api_auth';
import { updateToken } from '@/data/api/client';
import { useUserDataStore } from '@/data/stores/user_data';
import { useSettingStore } from '@/data/stores/setting';

const userData = useUserDataStore();
const setting = useSettingStore();

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

// 兼容旧格式
if ((setting as any).isDark !== undefined) {
  if ((setting as any).isDark === true) {
    setting.theme = 'dark';
  }
  (setting as any).isDark = undefined;
}
if (setting.enabledTranslator === undefined) {
  setting.enabledTranslator = ['baidu', 'youdao', 'gpt', 'sakura'];
}
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
