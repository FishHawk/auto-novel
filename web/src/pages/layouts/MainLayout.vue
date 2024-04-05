<script lang="ts" setup>
import {
  AccountCircleOutlined,
  LogOutOutlined,
  MenuOutlined,
} from '@vicons/material';
import { MenuOption, NIcon } from 'naive-ui';
import { RouterLink } from 'vue-router';

import { Locator } from '@/data';
import { useIsWideScreen } from '@/pages/util';

const isWideScreen = useIsWideScreen(850);
const route = useRoute();
const router = useRouter();

const { userData, isSignedIn, deleteProfile } = Locator.userDataRepository();

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
  return [
    menuOption('首页', '/'),
    menuOption('我的收藏', '/favorite', isSignedIn.value),
    menuOption('阅读历史', '/read-history', isSignedIn.value),
    menuOption('网络小说', '/novel-list'),
    menuOption('文库小说', '/wenku-list'),
    menuOption('工作区', '/workspace'),
    menuOption('论坛', '/forum'),
  ];
});

const userDropdownOptions = [
  dropdownOption('用户中心', 'account', AccountCircleOutlined),
  dropdownOption('退出登录', 'signOut', LogOutOutlined),
];
const handleUserDropdownSelect = (key: string | number) => {
  if (key === 'account') {
    router.push('/account');
  } else if (key === 'signOut') {
    deleteProfile();
  }
};

const showMenuModal = ref(false);

watch(
  () => route.path,
  () => (showMenuModal.value = false)
);
</script>

<template>
  <theme-main>
    <n-layout style="width: 100%; min-height: 100vh">
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

          <template v-if="isSignedIn">
            <router-link v-if="isWideScreen" to="/read-history">
              <n-button :focusable="false" quaternary>历史</n-button>
            </router-link>
            <router-link v-if="isWideScreen" to="/favorite">
              <n-button :focusable="false" quaternary>收藏</n-button>
            </router-link>
            <n-dropdown
              trigger="hover"
              :keyboard="false"
              :options="userDropdownOptions"
              @select="handleUserDropdownSelect"
            >
              <n-button :focusable="false" quaternary>
                @{{ userData.info?.username }}
              </n-button>
            </n-dropdown>
          </template>

          <router-link
            v-else
            :to="{ name: 'sign-in', query: { from: route.fullPath } }"
          >
            <n-button quaternary>登录/注册</n-button>
          </router-link>
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
  </theme-main>
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
