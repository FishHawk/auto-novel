<script lang="ts" setup>
import { LogOutFilled, MenuFilled } from '@vicons/material';
import {
  MenuOption,
  NIcon,
  darkTheme,
  dateZhCN,
  lightTheme,
  useThemeVars,
  zhCN,
} from 'naive-ui';
import { Component, computed, h, ref } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';

import { SignInDto } from '@/data/api/api_auth';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { useIsDesktop } from '@/data/util';

const isDesktop = useIsDesktop(850);
const setting = useSettingStore();
const route = useRoute();
const router = useRouter();
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
    menuOption('文件翻译', '/personal'),
    menuOption('论坛', '/forum'),
    menuOption('工具箱', '/toolbox'),
  ];
});

const path = route.path;
const getTopMenuOptionKey = () => {
  if (path.startsWith('/forum')) {
    return '/forum';
  } else if (path.startsWith('/favorite')) {
    return '/favorite';
  } else if (path.startsWith('/wenku')) {
    return '/wenku-list';
  } else if (path.startsWith('/novel')) {
    return '/novel-list';
  } else if (path.startsWith('/toolbox')) {
    return '/toolbox';
  } else {
    return path;
  }
};

const collapsedMenuOptions = computed(() => {
  const signed = userData.info !== undefined;
  return [
    menuOption('首页', '/'),
    menuOption('我的收藏', '/favorite', signed),
    menuOption('阅读历史', '/read-history', signed),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    menuOption('文件翻译', '/personal'),
    {
      label: '排行',
      children: [
        menuOption('成为小说家：流派', '/novel-rank/syosetu/1'),
        menuOption('成为小说家：综合', '/novel-rank/syosetu/2'),
        menuOption('成为小说家：异世界转移/转生', '/novel-rank/syosetu/3'),
        menuOption('Kakuyomu：流派', '/novel-rank/kakuyomu/1'),
      ],
    },
    menuOption('论坛', '/forum'),
    menuOption('工具箱', '/toolbox'),
  ];
});

const userDropdownOptions = computed(() => {
  return [
    menuOption('工作区-GPT', '/gpt-workspace'),
    menuOption('工作区-Sakura', '/sakura-workspace'),
    menuOption('公用工作区-Sakura', '/sakura'),
    dropdownOption('退出登录', 'signOut', LogOutFilled),
  ];
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

const navToMySpace = () => router.push({ path: '/account' });

const vars = useThemeVars();
</script>

<template>
  <n-config-provider
    :theme="setting.isDark ? darkTheme : lightTheme"
    :locale="zhCN"
    :date-locale="dateZhCN"
    inline-theme-disabled
    :theme-overrides="{
      Drawer: { bodyPadding: '0px' },
      List: { color: '#0000' },
    }"
  >
    <n-message-provider container-style="white-space: pre-wrap">
      <n-layout style="overflow-x: overlay">
        <n-layout-header bordered>
          <n-flex class="layout-content" align="center" style="height: 50px">
            <template v-if="isDesktop">
              <router-link to="/">
                <n-icon
                  size="30"
                  :color="vars.primaryColor"
                  style="margin-right: 8px; margin-bottom: 8px"
                >
                  <robot-svg />
                </n-icon>
              </router-link>
              <div>
                <n-menu
                  :value="getTopMenuOptionKey()"
                  mode="horizontal"
                  :options="topMenuOptions"
                />
              </div>
            </template>

            <n-icon
              v-else
              size="24"
              :component="MenuFilled"
              @click="showMenuModal = true"
            />

            <div style="flex: 1"></div>

            <template v-if="userData.username">
              <router-link v-if="isDesktop" to="/read-history">
                <n-button quaternary>历史</n-button>
              </router-link>
              <router-link v-if="isDesktop" to="/favorite">
                <n-button quaternary>收藏</n-button>
              </router-link>
              <n-dropdown
                trigger="hover"
                :options="userDropdownOptions"
                @select="handleUserDropdownSelect"
              >
                <n-button quaternary @click="navToMySpace()">
                  @{{ userData.username }}
                </n-button>
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

        <n-layout
          :native-scrollbar="false"
          :scrollbar-props="{ trigger: 'none' }"
          style="height: calc(100vh - 51px)"
        >
          <n-layout-content style="padding-bottom: 48px">
            <router-view />
          </n-layout-content>
        </n-layout>
      </n-layout>

      <n-drawer v-if="!isDesktop" v-model:show="showMenuModal" placement="left">
        <n-drawer-content
          max-width="600"
          :native-scrollbar="false"
          :scrollbar-props="{ trigger: 'none' }"
        >
          <n-menu :value="path" :options="collapsedMenuOptions" />
        </n-drawer-content>
      </n-drawer>

      <card-modal
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
      </card-modal>
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
