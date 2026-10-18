<script lang="ts" setup>
import { XTooltip } from '@novelia/web-kit';

defineProps<{
  tooltip?: string;
  icon: Component;
}>();
</script>

<template>
  <template v-if="tooltip">
    <XTooltip>
      <template #trigger>
        <c-button size="tiny" :icon="icon" secondary circle v-bind="$attrs" />
      </template>
      {{ tooltip }}
    </XTooltip>
  </template>

  <c-button v-else size="tiny" :icon="icon" secondary circle v-bind="$attrs" />
</template>
