<script lang="ts" setup>
defineProps<{
  hint: string;
  label?: string;
  icon?: Component;
  onAction?: (e: MouseEvent) => any;
}>();
</script>

<template>
  <n-popconfirm
    :show-icon="false"
    @positive-click="onAction"
    :negative-text="null"
    style="max-width: 300px"
  >
    <template #trigger>
      <n-button round v-bind="$attrs">
        <template v-if="icon && label" #icon>
          <n-icon :component="icon" />
        </template>
        {{ label }}
        <template v-if="icon && !label">
          <n-icon :component="icon" />
        </template>
      </n-button>
    </template>
    {{ hint }}
  </n-popconfirm>
</template>
