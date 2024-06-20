<script lang="ts" setup>
import { darkTheme, dateZhCN, useOsTheme, zhCN } from 'naive-ui';

import { Locator, formatError } from '@/data';
import { isDarkColor } from '@/pages/util';
import { RegexUtil } from '@/util';

const { userData, setProfile } = Locator.userDataRepository();
const { renew, updateToken } = Locator.authRepository;

// 订阅Token
watch(
  () => userData.value.profile?.token,
  (token) => updateToken(token),
  { immediate: true },
);

// 更新Token，冷却时间为24小时
const renewToken = async () => {
  const renewCooldown = 24 * 3600 * 1000;
  if (userData.value.profile) {
    const sinceLoggedIn = Date.now() - (userData.value.renewedAt ?? 0);
    if (sinceLoggedIn > renewCooldown) {
      try {
        const token = await renew();
        setProfile(token);
      } catch (e) {
        console.warn('更新授权失败：' + (await formatError(e)));
      }
    }
  }
};
renewToken();

// 清理pinia留下的垃圾
Object.keys(window.localStorage).forEach((key) => {
  if (key.startsWith('pubkey')) {
    window.localStorage.removeItem(key);
  }
});

// 主题
const route = useRoute();
const osThemeRef = useOsTheme();

const { setting } = Locator.settingRepository();
const { setting: readerSetting } = Locator.readerSettingRepository();

const buildTheme = (
  theme: 'light' | 'dark' | 'system' | 'custom',
  bodyColor?: string,
): {
  isDark: boolean;
  bodyColor?: string;
} => {
  switch (theme) {
    case 'light':
      return { isDark: false };
    case 'dark':
      return { isDark: true };
    case 'system':
      return { isDark: osThemeRef.value === 'dark' };
    case 'custom':
      return { isDark: isDarkColor(bodyColor!!), bodyColor };
  }
};
const theme = computed(() => {
  if (route.meta.isReader) {
    const theme = readerSetting.value.theme;
    return buildTheme(theme.mode, theme.bodyColor);
  } else {
    return buildTheme(setting.value.theme, undefined);
  }
});

// 处理Safari的奇妙问题
if (RegexUtil.isSafari(navigator.userAgent)) {
  // 防止Safari返回上一页时的灰屏
  // https://github.com/reactjs/react.dev/blob/e45ac5552c13fc50832624b7deb0c6f631d461bf/src/pages/_app.tsx#L30
  window.history.scrollRestoration = 'auto';

  // 禁用浏览器聚焦时的自动缩放，但浏览器还是会允许用户手动缩放
  const meta = document.querySelector('meta[name="viewport"]')!;
  const content = meta.getAttribute('content')!;
  meta.setAttribute('content', `${content}, user-scalable=no`);
}
</script>

<template>
  <n-config-provider
    :theme="theme.isDark ? darkTheme : null"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="{
      Drawer: { bodyPadding: '0px' },
      List: { color: '#0000' },
      common: {
        ...(theme.bodyColor === undefined
          ? undefined
          : { bodyColor: theme.bodyColor }),
      },
    }"
  >
    <n-global-style />
    <n-message-provider container-style="white-space: pre-wrap">
      <n-loading-bar-provider>
        <router-view v-slot="{ Component }">
          <keep-alive :include="['MainLayout']">
            <component :is="Component" />
          </keep-alive>
        </router-view>
      </n-loading-bar-provider>
    </n-message-provider>
  </n-config-provider>
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
.sortable-ghost {
  opacity: 0.7;
}
</style>
