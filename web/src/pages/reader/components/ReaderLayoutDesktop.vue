<script lang="ts" setup>
import {
  FormatListBulletedOutlined,
  LibraryBooksOutlined,
  TuneOutlined,
} from '@vicons/material';

defineProps<{
  novelUrl?: string;
}>();

const emit = defineEmits<{
  requireCatalogModal: [];
  requireSettingModal: [];
}>();
</script>

<template>
  <n-flex :wrap="false">
    <div style="flex: auto">
      <slot />
    </div>

    <div style="flex: 0 0 0">
      <n-flex
        size="large"
        vertical
        style="margin-left: 20px; position: fixed; bottom: 20px"
      >
        <side-button
          v-if="novelUrl"
          tag="a"
          :href="novelUrl"
          text="详情"
          :icon="LibraryBooksOutlined"
        />
        <side-button
          text="目录"
          :icon="FormatListBulletedOutlined"
          @click="emit('requireCatalogModal')"
        />
        <side-button
          text="设置"
          :icon="TuneOutlined"
          @click="emit('requireSettingModal')"
        />
      </n-flex>
    </div>
  </n-flex>
</template>
