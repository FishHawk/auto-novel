<script lang="ts" setup>
import { useRoute, useRouter } from 'vue-router';

import { useUserDataStore } from '@/data/stores/user_data';
import { UserRole } from '@/data/api/api_user';

const router = useRouter();
const route = useRoute();
const userData = useUserDataStore();

const path = route.path;
const handleUpdateValue = (path: string) => router.push({ path });

const roleToReadableText = (role: UserRole) => {
  if (role === 'normal') return '普通用户';
  else if (role === 'trusted') return '信任用户';
  else if (role === 'maintainer') return '维护者';
  else if (role === 'admin') return '管理员';
  else if (role === 'banned') return '封禁用户';
  else return '未知';
};
</script>

<template>
  <div class="layout-content">
    <template v-if="userData.isLoggedIn">
      <n-h1>
        @{{ userData.username }}
        <n-tag :bordered="false" size="small" style="margin-left: 4px">
          {{ roleToReadableText(userData.role!!) }}
        </n-tag>
      </n-h1>
      <n-tabs
        type="line"
        :value="path"
        @update:value="handleUpdateValue"
        style="margin-bottom: 24px"
      >
        <n-tab name="/account">帐号</n-tab>
        <n-tab name="/favorite">收藏</n-tab>
        <n-tab name="/read-history">历史</n-tab>
        <n-tab name="/personal-legacy">文件翻译（旧版）</n-tab>
      </n-tabs>
      <router-view />
    </template>
    <n-result v-else status="error" title="未登录" />
  </div>
</template>
