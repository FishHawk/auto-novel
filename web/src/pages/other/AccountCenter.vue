<script lang="ts" setup>
import {
  WebSearchHistoryRepository,
  WenkuSearchHistoryRepository,
} from '@/data/stores';
import { Setting, SettingRepository } from '@/data/stores';
import { useUserDataStore } from '@/data/stores/user_data';
import { UserRole } from '@/model/User';

const message = useMessage();
const setting = SettingRepository.ref();
const userData = useUserDataStore();

const roleToReadableText = (role: UserRole) => {
  if (role === 'normal') return '普通用户';
  else if (role === 'trusted') return '信任用户';
  else if (role === 'maintainer') return '维护者';
  else if (role === 'admin') return '管理员';
  else if (role === 'banned') return '封禁用户';
  else return '未知';
};

const clearWebSearchHistory = () => {
  WebSearchHistoryRepository.clear();
  message.success('清空成功');
};

const clearWenkuSearchHistory = () => {
  WenkuSearchHistoryRepository.clear();
  message.success('清空成功');
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

      <n-list bordered>
        <n-list-item>
          <n-flex vertical>
            <b>主题</b>
            <c-radio-group
              v-model:value="setting.theme"
              :options="Setting.themeOptions"
              size="small"
            />
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical>
            <b>显示的翻译按钮</b>
            <translator-check
              v-model:value="setting.enabledTranslator"
              size="small"
            />
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical align="start">
            <b>工作区语音提醒</b>
            <n-switch size="small" v-model:value="setting.workspaceSound" />
          </n-flex>
        </n-list-item>

        <n-list-item>
          <n-flex vertical align="start">
            <b>清空搜索历史</b>
            <n-flex>
              <c-button
                label="清空网络搜索历史"
                size="small"
                @action="clearWebSearchHistory"
              />
              <c-button
                label="清空文库搜索历史"
                size="small"
                @action="clearWenkuSearchHistory"
              />
            </n-flex>
          </n-flex>
        </n-list-item>

        <n-list-item v-if="userData.isAdmin">
          <n-flex vertical>
            <b>控制台</b>
            <n-flex>
              <router-link to="/admin/user">
                <c-button label="控制台" size="small" />
              </router-link>
              <c-button
                :label="`管理员模式-${userData.asAdmin}`"
                size="small"
                @action="userData.toggleAdminMode()"
              />
            </n-flex>
          </n-flex>
        </n-list-item>
      </n-list>
    </template>
    <n-result v-else status="error" title="未登录" />
  </div>
</template>
