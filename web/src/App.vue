<script lang="ts" setup>
import { Locator, formatError } from '@/data';

const { userData, setProfile } = Locator.userDataRepository();
const { renew, updateToken } = Locator.authRepository;

// 订阅Token
watch(
  () => userData.value.info?.token,
  (token) => updateToken(token),
  { immediate: true }
);

// 更新Token，冷却时间为24小时
const renewToken = async () => {
  const renewCooldown = 24 * 3600 * 1000;
  if (userData.value.info) {
    const sinceLoggedIn = Date.now() - (userData.value.renewedAt ?? 0);
    if (sinceLoggedIn > renewCooldown) {
      try {
        const profile = await renew();
        setProfile(profile);
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
.sortable-ghost {
  opacity: 0.7;
}
</style>
