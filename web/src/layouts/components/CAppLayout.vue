<script setup lang="ts">
import { checkIsMobile } from '@/data/util'

const isMobile = checkIsMobile()
</script>

<template>
  <n-layout
    class="app-layout"
    :native-scrollbar="isMobile"
    :scrollbar-props="{ trigger: 'none' }"
  >
    <slot />
  </n-layout>
</template>

<style scoped>
.app-layout {
  overflow: v-bind('`${isMobile ? 'unset' : 'hidden'}`');
  height: v-bind('`${isMobile ? 'auto' : '100vh'}`');
}
.app-layout > :deep(.n-layout-scroll-container) {
  overflow-x: unset;
}
</style>
