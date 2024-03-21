<script lang="ts" setup>
defineProps<{
  tooltip: string;
  icon: Component;
}>();
</script>

<template>
  <n-tooltip trigger="hover">
    <template #trigger>
      <c-button size="tiny" :icon="icon" secondary circle v-bind="$attrs" />
    </template>
    {{ tooltip }}
  </n-tooltip>
</template>
