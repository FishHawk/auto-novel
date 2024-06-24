<script lang="ts" setup>
import {
  BookOutlined,
  ForumOutlined,
  HistoryOutlined,
  HomeOutlined,
  LanguageOutlined,
  LocalFireDepartmentOutlined,
  LogOutOutlined,
  MenuOutlined,
  SettingsOutlined,
  StarBorderOutlined,
  WorkspacesOutlined,
} from '@vicons/material';
import { MenuOption, NIcon } from 'naive-ui';
import { RouterLink } from 'vue-router';

import { Locator } from '@/data';
import { UserRole } from '@/model/User';
import { useBreakPoints } from '@/pages/util';

const bp = useBreakPoints();
const hasSider = bp.greater('tablet');
const menuShowTrigger = bp.greater('desktop');
const menuCollapsed = ref(!menuShowTrigger.value);
const showMenuModal = ref(false);

watch(hasSider, () => (showMenuModal.value = false));
watch(menuShowTrigger, (value) => (menuCollapsed.value = !value));

const route = useRoute();

const authRepository = Locator.authRepository();
const { profile, isSignedIn, atLeastAdmin, asAdmin } = authRepository;

const renderLabel = (text: string, href: string) => () =>
  h(RouterLink, { to: href }, { default: () => text });
const renderIcon = (icon: Component) => () =>
  h(NIcon, null, { default: () => h(icon) });

const menuOptions = computed<MenuOption[]>(() => {
  return [
    {
      label: renderLabel('首页', '/'),
      icon: renderIcon(HomeOutlined),
      key: '/',
    },
    {
      label: renderLabel(
        '我的收藏',
        isSignedIn.value ? '/favorite/web' : '/favorite/local',
      ),
      icon: renderIcon(StarBorderOutlined),
      key: '/favorite',
    },
    {
      label: renderLabel('阅读历史', '/read-history'),
      icon: renderIcon(HistoryOutlined),
      key: '/read-history',
      show: isSignedIn.value,
    },
    {
      label: renderLabel('网络小说', '/novel'),
      icon: renderIcon(LanguageOutlined),
      key: '/novel',
    },
    {
      label: renderLabel('文库小说', '/wenku'),
      icon: renderIcon(BookOutlined),
      key: '/wenku',
    },
    {
      label: '小说排行',
      icon: renderIcon(LocalFireDepartmentOutlined),
      key: '/rank',
      children: [
        {
          label: renderLabel('成为小说家：流派', '/rank/web/syosetu/1'),
          key: '/rank/web/syosetu/1',
        },
        {
          label: renderLabel('成为小说家：综合', '/rank/web/syosetu/2'),
          key: '/rank/web/syosetu/2',
        },
        {
          label: renderLabel(
            '成为小说家：异世界转移/转生',
            '/rank/web/syosetu/3',
          ),
          key: '/rank/web/syosetu/3',
        },
        {
          label: renderLabel('Kakuyomu：流派', '/rank/web/kakuyomu/1'),
          key: '/rank/web/kakuyomu/1',
        },
      ],
    },
    {
      type: 'divider',
      key: 'divider',
      props: { style: { marginTop: '16px', marginBottom: '16px' } },
    },
    {
      label: '工作区',
      icon: renderIcon(WorkspacesOutlined),
      key: '/workspace',
      children: [
        {
          label: renderLabel('术语表工作区', '/workspace/katakana'),
          key: '/workspace/katakana',
        },
        {
          label: renderLabel('GPT工作区', '/workspace/gpt'),
          key: '/workspace/gpt',
        },
        {
          label: renderLabel('Sakura工作区', '/workspace/sakura'),
          key: '/workspace/sakura',
        },
        {
          label: renderLabel('交互翻译', '/workspace/interactive'),
          key: '/workspace/interactive',
        },
      ],
    },
    {
      label: renderLabel('论坛', '/forum'),
      icon: renderIcon(ForumOutlined),
      key: '/forum',
    },
    {
      label: renderLabel('设置', '/setting'),
      icon: renderIcon(SettingsOutlined),
      key: '/setting',
    },
    {
      label: renderLabel('控制台', '/admin'),
      icon: renderIcon(SettingsOutlined),
      key: '/admin',
      show: asAdmin.value,
    },
  ];
});

const menuKey = computed(() => {
  const path = route.path;
  for (const key of ['/novel', '/wenku', '/favorite', '/forum']) {
    if (path.startsWith(key)) {
      return key;
    }
  }
  return path;
});

const readableRole = (role: UserRole) => {
  if (role === 'normal') return '普通用户';
  else if (role === 'trusted') return '信任用户';
  else if (role === 'maintainer') return '维护者';
  else if (role === 'admin') return '管理员';
  else if (role === 'banned') return '封禁用户';
  else return '未知';
};

const userDropdownOptions = computed<MenuOption[]>(() => {
  const options: MenuOption[] = [
    {
      label: '退出账号',
      key: 'sign-out',
      icon: renderIcon(LogOutOutlined),
    },
  ];
  options.unshift({
    label: readableRole(profile.value!.role) + (asAdmin.value ? '+' : ''),
    key: 'toggle',
  });
  return options;
});
const handleUserDropdownSelect = (key: string | number) => {
  if (key === 'sign-out') {
    authRepository.signOut();
  } else if (key === 'toggle') {
    if (atLeastAdmin.value) {
      authRepository.toggleAdminMode();
    }
  }
};

watch(
  () => route.path,
  () => (showMenuModal.value = false),
);
</script>

<template>
  <n-layout :has-sider="hasSider" style="width: 100%; min-height: 100vh">
    <n-layout-header bordered style="position: fixed; z-index: 2">
      <n-flex align="center" style="height: 50px" :size="0">
        <n-button
          v-if="!hasSider"
          size="large"
          quaternary
          circle
          :focusable="false"
          style="margin: 0 8px"
          @click="showMenuModal = true"
        >
          <n-icon size="24" :component="MenuOutlined" />
        </n-button>
        <div v-else style="padding: 0 16px">
          <robot-icon size="32" />
        </div>

        <div style="flex: 1" />

        <router-link
          v-if="!hasSider"
          :to="isSignedIn ? '/favorite/web' : '/favorite/local'"
        >
          <n-button size="large" quaternary circle :focusable="false">
            <n-icon size="20" :component="StarBorderOutlined" />
          </n-button>
        </router-link>

        <n-dropdown
          v-if="isSignedIn"
          trigger="hover"
          :keyboard="false"
          :options="userDropdownOptions"
          @select="handleUserDropdownSelect"
        >
          <n-button :focusable="false" quaternary>
            @{{ profile?.username }}
          </n-button>
        </n-dropdown>

        <router-link
          v-else
          :to="{ name: 'sign-in', query: { from: route.fullPath } }"
        >
          <n-button quaternary>登录/注册</n-button>
        </router-link>
      </n-flex>
    </n-layout-header>

    <n-layout-sider
      v-if="hasSider"
      :show-trigger="menuShowTrigger"
      :trigger-style="{ position: 'fixed', top: '80%', left: '214px' }"
      :collapsed-trigger-style="{ position: 'fixed', top: '80%', left: '36px' }"
      bordered
      :width="240"
      :collapsed="menuCollapsed"
      :collapsed-width="64"
      collapse-mode="width"
      :native-scrollbar="false"
      style="z-index: 1"
      @collapse="menuCollapsed = true"
      @expand="menuCollapsed = false"
    >
      <n-scrollbar
        style="margin-top: 50px; position: fixed; top: 0; padding-bottom: 64px"
        :style="{ width: menuCollapsed ? '64px' : '240px' }"
      >
        <n-menu
          :value="menuKey"
          :options="menuOptions"
          :width="240"
          :collapsed="menuCollapsed"
          :collapsed-width="64"
          :collapsed-icon-size="22"
          :default-expanded-keys="['/workspace']"
        />
      </n-scrollbar>
    </n-layout-sider>

    <n-layout-content
      style="
        margin-top: 50px;
        margin-bottom: 64px;
        z-index: 0;
        min-height: calc(100vh - 50px);
      "
    >
      <router-view v-slot="{ Component }">
        <keep-alive
          :include="[
            'Forum',
            'Index',
            'BookshelfWeb',
            'BookshelfWenku',
            'ReadHistoryList',
            'WebNovelList',
            'WebNovelRank',
            'WenkuNovelList',
          ]"
        >
          <component :is="Component" />
        </keep-alive>
      </router-view>
    </n-layout-content>
  </n-layout>

  <c-drawer-left v-if="!hasSider" v-model:show="showMenuModal">
    <n-menu :value="menuKey" :options="menuOptions" />
  </c-drawer-left>
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
