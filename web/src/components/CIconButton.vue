<script lang="ts" setup>
defineProps<{
  tooltip?: string;
  icon: Component;
}>();
</script>

<template>
  <template v-if="tooltip">
    <n-tooltip trigger="hover">
      <template #trigger>
        <c-button size="tiny" :icon="icon" secondary circle v-bind="$attrs" />
      </template>
      {{ tooltip }}
    </n-tooltip>
  </template>

  <c-button v-else size="tiny" :icon="icon" secondary circle v-bind="$attrs" />
</template>
