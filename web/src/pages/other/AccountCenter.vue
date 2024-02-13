<script lang="ts" setup>
import { UserRole } from '@/data/api/api_user';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';

const setting = useSettingStore();
const userData = useUserDataStore();

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

      <n-switch v-model:value="setting.isDark">
        <template #checked>深色主题</template>
        <template #unchecked>浅色主题</template>
      </n-switch>
      <n-p>
        <n-text depth="3" style="font-size: 12px">
          只有一个主题选项太奇怪了？因为我还没想好放什么
        </n-text>
      </n-p>
      <n-flex v-if="userData.isAdmin">
        <c-button label="控制台" tag="a" href="/admin/user" />
        <c-button
          :label="`管理员模式-${userData.asAdmin}`"
          @click="userData.toggleAdminMode()"
        />
      </n-flex>
    </template>
    <n-result v-else status="error" title="未登录" />
  </div>
</template>
