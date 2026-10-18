<script lang="ts" setup>
import { XDrawer } from '@novelia/web-kit';
import { FormatListBulletedOutlined } from '@vicons/material';

defineProps<{ menuKey: string }>();
const showListModal = ref(false);
</script>

<template>
  <c-button
    label="列表"
    :icon="FormatListBulletedOutlined"
    @action="showListModal = true"
  />

  <XDrawer
    v-model:open="showListModal"
    title="收藏夹"
    show-header
    side="right"
    size="clamp(280px, 80vw, 800px)"
    @open-auto-focus.prevent
  >
    <template #action>
      <bookshelf-add-button />
    </template>
    <n-scrollbar trigger="none" class="min-h-0 flex-1">
      <bookshelf-menu :value="menuKey" />
    </n-scrollbar>
  </XDrawer>
</template>
