<script lang="ts" setup>
import { useIsWideScreen } from '@/pages/util';

defineProps<{ menuKey: string }>();
const isWideScreen = useIsWideScreen(850);
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <div style="flex: auto">
      <slot />
    </div>
    <template #sidebar>
      <section-header title="收藏夹">
        <bookshelf-add-button />
      </section-header>
      <bookshelf-menu :value="menuKey" />
    </template>
  </c-layout>
</template>
