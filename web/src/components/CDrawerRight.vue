<script lang="ts" setup>
import { useWindowSize } from '@vueuse/core';

defineProps<{ title: string }>();

const { width } = useWindowSize();
const drawerWidth = computed(() =>
  Math.max(280, Math.min(800, 0.8 * width.value)),
);
</script>

<template>
  <n-drawer
    :width="drawerWidth"
    :auto-focus="false"
    :block-scroll="false"
    placement="right"
  >
    <n-drawer-content
      :native-scrollbar="false"
      :scrollbar-props="{ trigger: 'none' }"
    >
      <template #header>
        <n-flex align="center" justify="space-between" :wrap="false">
          {{ title }}
          <n-flex :wrap="false">
            <slot name="action" />
          </n-flex>
        </n-flex>
      </template>
      <slot />
    </n-drawer-content>
  </n-drawer>
</template>
