<script lang="ts">
export function menuOption(
  text: string,
  href: string,
  show?: boolean
): MenuOption {
  return { label: () => h('a', { href }, text), key: href, show };
}

export function dropdownOption(
  label: string,
  key: string,
  icon: Component
): MenuOption {
  return {
    label,
    key,
    icon: () => h(NIcon, null, { default: () => h(icon) }),
  };
}
</script>

<script lang="ts" setup>
import { Component, computed, h, ref } from 'vue';
import { MenuOption, NIcon } from 'naive-ui';
import { LogOutFilled, MenuFilled } from '@vicons/material';
import { useRoute } from 'vue-router';

import {
  atLeastMaintainer,
  AuthInfo,
  useAuthInfoStore,
} from '@/data/stores/authInfo';
import { useIsDesktop } from '@/data/util';

withDefaults(
  defineProps<{
    sideMenuOptions: MenuOption[];
  }>(),
  { sideMenuOptions: () => [] }
);

const isDesktop = useIsDesktop(850);
const authInfoStore = useAuthInfoStore();
const topMenuOptions = computed(() => {
  return [
    menuOption('首页', '/'),
    menuOption('列表', '/novel-list'),
    menuOption('文件翻译', '/wenku/non-archived'),
    menuOption('论坛', '/forum'),
  ];
});

const path = useRoute().path;
function getTopMenuOptionKey() {
  if (path.startsWith('/forum')) {
    return '/forum';
  } else if (
    ['/favorite-list', '/read-history', '/novel-list', '/wenku-list'].includes(
      path
    ) ||
    path.startsWith('/novel-rank')
  ) {
    return '/novel-list';
  } else {
    return path;
  }
}

const collapsedMenuOptions = computed(() => {
  const signed = authInfoStore.info !== undefined;
  return [
    menuOption('首页', '/'),
    menuOption('我的收藏', '/favorite-list', signed),
    menuOption('阅读历史', '/read-history', signed),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    {
      label: '排行',
      children: [
        menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
        menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
        menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
        menuOption('Kakuyomu：流派', '/novel-rank/kakuyomu/1'),
      ],
    },
    menuOption('文件翻译', '/wenku/non-archived'),
    menuOption('论坛', '/forum'),
  ];
});

const userDropdownOptions = computed(() => {
  return [
    menuOption('管理员', '/admin', atLeastMaintainer(authInfoStore.role)),
    dropdownOption('退出登录', 'signOut', LogOutFilled),
  ];
});
function handleUserDropdownSelect(key: string | number) {
  if (key === 'signOut') {
    authInfoStore.delete();
  }
}

const showLoginModal = ref(false);

function onSignInSuccess(info: AuthInfo): void {
  authInfoStore.set(info);
  showLoginModal.value = false;
}
</script>

<template>
  <n-layout style="overflow-x: overlay">
    <n-layout-header bordered>
      <div class="header">
        <n-popover v-if="!isDesktop" trigger="click" :width="280">
          <template #trigger>
            <n-icon size="24"> <MenuFilled /> </n-icon>
          </template>
          <n-menu :value="path" :options="collapsedMenuOptions" />
        </n-popover>
        <n-a v-if="isDesktop" href="/" target="_blank">
          <n-icon size="30" style="margin-right: 8px; margin-bottom: 8px">
            <img
              src="/robot.svg"
              alt="Robot"
              style="width: 100%; min-width: 100%"
            />
          </n-icon>
        </n-a>
        <div v-if="isDesktop">
          <n-menu
            :value="getTopMenuOptionKey()"
            mode="horizontal"
            :options="topMenuOptions"
          />
        </div>

        <div style="flex: 1"></div>

        <n-space v-if="authInfoStore.username">
          <n-a v-if="isDesktop" href="/read-history">
            <n-button quaternary>历史</n-button>
          </n-a>
          <n-a v-if="isDesktop" href="/favorite-list">
            <n-button quaternary>收藏</n-button>
          </n-a>
          <n-dropdown
            trigger="click"
            :options="userDropdownOptions"
            @select="handleUserDropdownSelect"
          >
            <n-button quaternary> @{{ authInfoStore.username }} </n-button>
          </n-dropdown>
        </n-space>

        <n-button
          v-else
          quaternary
          style="margin-right: 4px"
          @click="showLoginModal = true"
        >
          登录/注册
        </n-button>
      </div>
    </n-layout-header>

    <n-layout :native-scrollbar="false" style="height: calc(100vh - 51px)">
      <slot name="full-width" />
      <n-layout class="container" style="padding-bottom: 48px" has-sider>
        <n-layout-content>
          <slot />
        </n-layout-content>
        <n-layout-sider
          v-if="sideMenuOptions.length > 0 && isDesktop"
          style="margin-left: 12px"
        >
          <n-menu :value="path" :options="sideMenuOptions" />
        </n-layout-sider>
      </n-layout>
    </n-layout>
  </n-layout>

  <n-modal v-model:show="showLoginModal">
    <n-card
      style="width: min(400px, calc(100% - 16px))"
      :bordered="false"
      size="large"
      role="dialog"
      aria-modal="true"
    >
      <n-tabs
        class="card-tabs"
        default-value="signin"
        size="large"
        animated
        style="margin: 0 -4px"
        pane-style="padding-left: 4px; padding-right: 4px; box-sizing: border-box;"
      >
        <n-tab-pane name="signin" tab="登录">
          <SignInForm @signIn="onSignInSuccess" />
        </n-tab-pane>

        <n-tab-pane name="signup" tab="注册">
          <SignUpForm @signUp="onSignInSuccess" />
        </n-tab-pane>
      </n-tabs>
    </n-card>
  </n-modal>
</template>

<style>
div.n-scrollbar-rail {
  z-index: 10000;
}
.header {
  display: flex;
  align-items: center;
  height: 50px;
}
.header,
.container {
  max-width: 1000px;
  margin: 0 auto;
  padding-left: 30px;
  padding-right: 30px;
}
@media only screen and (max-width: 600px) {
  .header,
  .container {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
