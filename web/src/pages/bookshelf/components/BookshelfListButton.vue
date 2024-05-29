<script lang="ts" setup>
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

  <c-drawer-right v-model:show="showListModal" title="收藏夹">
    <template #action>
      <bookshelf-add-button />
    </template>
    <bookshelf-menu :value="menuKey" />
  </c-drawer-right>
</template>
