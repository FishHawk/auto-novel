<script setup lang="ts">
import { ref, onMounted } from 'vue';
import { RouterView, useRoute } from 'vue-router';
import { PanoramaOutlined } from '@vicons/material';
import {
  AdminSiteSwitcher,
  WebKitApp,
  WebKitLayout,
  useWebKit,
  getApiErrorMessage,
  type WebKitMenuOption,
} from '@novelia/web-kit';

const route = useRoute();
const { whoami, checkSignedIn } = useWebKit();
const checking = ref(true);
const error = ref('');
const menu: WebKitMenuOption[] = [
  {
    key: 'banners',
    label: 'Banner 管理',
    to: { name: 'banners' },
    icon: PanoramaOutlined,
  },
];

async function checkSession() {
  checking.value = true;
  error.value = '';
  try {
    await checkSignedIn();
  } catch (cause) {
    error.value = getApiErrorMessage(cause, '登录状态检查失败');
  } finally {
    checking.value = false;
  }
}
onMounted(checkSession);
</script>

<template>
  <WebKitApp>
    <WebKitLayout
      :navigation-options="menu"
      :account-options="[]"
      :selected-navigation-key="String(route.name ?? '')"
    >
      <template #header-actions>
        <AdminSiteSwitcher current="auto-novel" />
      </template>
      <main class="admin-container">
        <div v-if="checking" class="empty-state" role="status">
          正在检查登录状态…
        </div>
        <div v-else-if="error" class="empty-state" role="alert">
          <h1>暂时无法检查登录状态</h1>
          <p>{{ error }}</p>
          <button class="btn" @click="checkSession">重试</button>
        </div>
        <RouterView v-else-if="whoami.isAdmin" />
        <div v-else class="empty-state">
          <h1>
            {{ whoami.isSignedIn ? '需要管理员权限' : '欢迎使用小说管理后台' }}
          </h1>
          <p>
            {{
              whoami.isSignedIn
                ? '当前账号没有访问管理后台的权限，请切换管理员账号。'
                : '请先通过右上角登录账号，再访问管理后台。'
            }}
          </p>
          <a class="btn" href="/">返回小说站点</a>
        </div>
      </main>
    </WebKitLayout>
  </WebKitApp>
</template>
