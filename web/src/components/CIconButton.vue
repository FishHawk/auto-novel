<script lang="ts" setup>
import { Component } from 'vue';

defineProps<{
  tooltip: string;
  icon: Component;
}>();
</script>

<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <n-button text v-bind="$attrs">
        <n-icon :component="icon" />
      </n-button>
    </template>
    {{ tooltip }}
  </n-tooltip>
</template>
