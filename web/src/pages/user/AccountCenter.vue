<script lang="ts" setup>
import { useSettingStore } from '@/data/stores/setting';

const setting = useSettingStore();
</script>
<template>
  <UserLayout>
    <n-switch v-model:value="setting.isDark">
      <template #checked>深色主题</template>
      <template #unchecked>浅色主题</template>
    </n-switch>
    <n-p>
      <n-text depth="3" style="font-size: 12px">
        只有一个主题选项太奇怪了？因为我还没想好放什么
      </n-text>
    </n-p>
  </UserLayout>
</template>
