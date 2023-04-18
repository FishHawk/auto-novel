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

const authInfoStore = useAuthInfoStore();

function menuOption(text: string, href: string): MenuOption {
  return { label: () => h('a', { href }, text), key: href };
}

const topMenuOptions = computed(() => {
  const menus: MenuOption[] = [
    menuOption('首页', '/'),
    menuOption('列表', '/novel-list'),
    menuOption('反馈', '/feedback'),
  ];

  if (atLeastMaintainer(authInfoStore.role)) {
    menus.push(menuOption('控制台', '/admin/patch'));
  }
  return menus;
});

const collapsedMenuOptions = computed(() => {
  const menus: MenuOption[] = [
    menuOption('首页', '/'),
    {
      label: '列表',
      key: '/list',
      children: [
        menuOption('网络小说', '/novel-list'),
        menuOption('文库小说', '/wenku-list'),
        menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
        menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
        menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
      ],
    },
    menuOption('反馈', '/feedback'),
  ];
  if (atLeastMaintainer(authInfoStore.role)) {
    menus.push({
      label: '控制台',
      key: '/admin',
      children: [
        menuOption('编辑历史', '/admin/patch'),
        menuOption('目录合并历史', '/admin/toc-merge'),
      ],
    });
  }
  return menus;
});

const path = useRoute().path;
function getTopMenuOptionKey() {
  if (path.startsWith('/admin')) {
    return '/admin/patch';
  } else if (path.startsWith('/feedback')) {
    return '/feedback';
  } else if (path === '/') {
    return '/';
  } else {
    return '/novel-list';
  }
}

function dropdownOption(label: string, key: string, icon: Component) {
  return {
    label,
    key,
    icon: () => h(NIcon, null, { default: () => h(icon) }),
  };
}

const userDropdownOptions = [
  dropdownOption('退出登录', 'signOut', LogOutFilled),
];

const showLoginModal = ref(false);

function onSignInSuccess(info: AuthInfo): void {
  authInfoStore.set(info);
  showLoginModal.value = false;
}

function handleUserDropdownSelect(key: string | number) {
  if (key === 'signOut') {
    authInfoStore.delete();
  }
}
</script>

<template>
  <n-layout>
    <n-layout-header bordered>
      <div class="header">
        <n-popover trigger="click" :width="280" style="padding: 0">
          <template #trigger>
            <n-icon
              size="24"
              class="on-mobile"
              style="padding-inline-start: 16px"
            >
              <MenuFilled />
            </n-icon>
          </template>
          <n-menu
            :value="path"
            :options="collapsedMenuOptions"
            :default-expanded-keys="['/list', '/admin']"
          />
        </n-popover>
        <n-a class="on-desktop" href="/" target="_blank">
          <n-icon size="30" style="margin-right: 8px; margin-bottom: 8px">
            <img
              src="/robot.svg"
              alt="Robot"
              style="width: 100%; min-width: 100%"
            />
          </n-icon>
        </n-a>
        <div class="on-desktop">
          <n-menu
            :value="getTopMenuOptionKey()"
            mode="horizontal"
            :options="topMenuOptions"
          />
        </div>
        <div style="flex: 1"></div>

        <n-dropdown
          v-if="authInfoStore.username"
          trigger="click"
          :options="userDropdownOptions"
          @select="handleUserDropdownSelect"
        >
          <n-button quaternary style="margin-right: 4px">
            @{{ authInfoStore.username }}
          </n-button>
        </n-dropdown>

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
    <slot name="full-width" />
    <div class="container">
      <slot />
    </div>
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
.header {
  margin: auto;
  display: flex;
  align-items: center;
  height: 50px;
}
@media only screen and (min-width: 600px) {
  .header,
  .container {
    width: 1000px;
    padding-left: 30px;
    padding-right: 30px;
  }
  .container {
    margin: 0 auto 48px;
  }
}
@media only screen and (max-width: 600px) {
  .header,
  .container {
    width: auto;
  }
  .container {
    margin: 0 12px 48px;
  }
}
</style>
