<script lang="ts" setup>
import { LogOutOutlined, MenuOutlined } from '@vicons/material';
import {
  MenuOption,
  NIcon,
  darkTheme,
  dateZhCN,
  useOsTheme,
  zhCN,
} from 'naive-ui';
import { Component, computed, h, ref } from 'vue';
import { RouterLink, useRoute } from 'vue-router';

import { SignInDto } from '@/data/api/api_auth';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsWideScreen } from '@/data/util';

const isWideScreen = useIsWideScreen(850);
const setting = useSettingStore();
const route = useRoute();
const userData = useUserDataStore();

const menuOption = (
  text: string,
  href: string,
  show?: boolean
): MenuOption => ({
  label: () => h(RouterLink, { to: href }, { default: () => text }),
  key: href,
  show,
});

const dropdownOption = (
  label: string,
  key: string,
  icon: Component
): MenuOption => ({
  label,
  key,
  icon: () => h(NIcon, null, { default: () => h(icon) }),
});

const topMenuOptions = computed(() => {
  return [
    menuOption('首页', '/'),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    {
      ...menuOption('工作区', '/workspace'),
      children: [
        menuOption('术语表工作区', '/workspace/katakana'),
        menuOption('GPT工作区', '/workspace/gpt'),
        menuOption('Sakura工作区', '/workspace/sakura'),
        menuOption('交互翻译', '/workspace/interactive'),
      ],
    },
    menuOption('论坛', '/forum'),
  ];
});

const menuKey = computed(() => {
  const path = route.path;
  if (path.startsWith('/novel')) {
    return '/novel-list';
  } else if (path.startsWith('/wenku')) {
    return '/wenku-list';
  } else if (path.startsWith('/workspace')) {
    return '/workspace';
  } else if (path.startsWith('/forum')) {
    return '/forum';
  } else {
    return path;
  }
});

const collapsedMenuOptions = computed(() => {
  const signed = userData.info !== undefined;
  return [
    menuOption('首页', '/'),
    menuOption('我的收藏', '/favorite', signed),
    menuOption('阅读历史', '/read-history', signed),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    menuOption('工作区', '/workspace'),
    menuOption('论坛', '/forum'),
  ];
});

const userDropdownOptions = computed(() => {
  return [dropdownOption('退出登录', 'signOut', LogOutOutlined)];
});
const handleUserDropdownSelect = (key: string | number) => {
  if (key === 'signOut') {
    userData.deleteProfile();
  }
};

const showLoginModal = ref(false);
const showMenuModal = ref(false);

const onSignInSuccess = (profile: SignInDto) => {
  userData.setProfile(profile);
  showLoginModal.value = false;
};

const osThemeRef = useOsTheme();
const theme = computed(() => {
  let specificTheme: 'light' | 'dark' = 'light';
  if (setting.theme !== 'system') {
    specificTheme = setting.theme;
  } else if (osThemeRef.value) {
    specificTheme = osThemeRef.value;
  }
  return specificTheme === 'light' ? null : darkTheme;
});
</script>

<template>
  <n-config-provider
    :theme="theme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="{
      Drawer: { bodyPadding: '0px' },
      List: { color: '#0000' },
    }"
  >
    <n-global-style />
    <n-message-provider container-style="white-space: pre-wrap">
      <n-layout id="kk">
        <n-layout-header bordered style="position: fixed; z-index: 1">
          <n-flex class="layout-content" align="center" style="height: 50px">
            <template v-if="isWideScreen">
              <robot-icon />
              <div>
                <n-menu
                  :value="menuKey"
                  mode="horizontal"
                  :options="topMenuOptions"
                />
              </div>
            </template>

            <n-icon
              v-else
              size="24"
              :component="MenuOutlined"
              @click="showMenuModal = true"
            />

            <div style="flex: 1"></div>

            <template v-if="userData.username">
              <router-link v-if="isWideScreen" to="/read-history">
                <n-button quaternary>历史</n-button>
              </router-link>
              <router-link v-if="isWideScreen" to="/favorite">
                <n-button quaternary>收藏</n-button>
              </router-link>
              <n-dropdown
                trigger="hover"
                :options="userDropdownOptions"
                @select="handleUserDropdownSelect"
              >
                <router-link :to="'/account'">
                  <n-button quaternary>@{{ userData.username }}</n-button>
                </router-link>
              </n-dropdown>
            </template>

            <n-button
              v-else
              quaternary
              style="margin-right: 4px"
              @click="showLoginModal = true"
            >
              登录/注册
            </n-button>
          </n-flex>
        </n-layout-header>

        <n-layout-content style="margin-top: 50px; z-index: 0">
          <router-view />
        </n-layout-content>

        <n-layout-footer style="height: 64px; background-color: transparent" />
      </n-layout>

      <c-drawer-left v-if="!isWideScreen" v-model:show="showMenuModal">
        <n-menu :value="menuKey" :options="collapsedMenuOptions" />
      </c-drawer-left>

      <c-modal
        v-model:show="showLoginModal"
        style="width: min(400px, calc(100% - 16px))"
      >
        <n-tabs
          class="card-tabs"
          default-value="signin"
          size="large"
          animated
          pane-style="padding-left: 4px; padding-right: 4px; box-sizing: border-box;"
        >
          <n-tab-pane name="signin" tab="登录">
            <SignInForm @signIn="onSignInSuccess" />
          </n-tab-pane>

          <n-tab-pane name="signup" tab="注册">
            <SignUpForm @signUp="onSignInSuccess" />
          </n-tab-pane>
        </n-tabs>
      </c-modal>
    </n-message-provider>
  </n-config-provider>
</template>

<style>
.layout-content {
  max-width: 1000px;
  margin: 0 auto;
  padding-left: 30px;
  padding-right: 30px;
}
@media only screen and (max-width: 600px) {
  .layout-content {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
