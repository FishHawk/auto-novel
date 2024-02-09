<script lang="ts" setup>
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';

const setting = useSettingStore();
const userData = useUserDataStore();
</script>

<template>
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
