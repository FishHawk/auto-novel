<script lang="ts" setup>
import { useWindowSize } from '@vueuse/core';

defineProps<{ title: string }>();

const { height } = useWindowSize();
const drawerHeight = computed(() =>
  Math.max(300, Math.min(800, 0.8 * height.value)),
);
</script>

<template>
  <n-drawer
    :height="drawerHeight"
    :auto-focus="false"
    :block-scroll="false"
    placement="bottom"
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
