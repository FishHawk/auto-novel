<script lang="ts" setup>
defineProps<{
  desktop: boolean;
  value: string;
  options: { label: string; value: string }[];
}>();
const emit = defineEmits<{
  (e: 'update:value', value: string): void;
}>();
</script>

<template>
  <n-radio-group
    :value="value"
    @update:value="emit('update:value', $event)"
  >
    <n-radio-button
      v-for="option in options"
      :key="option.value"
      :value="option.value"
    >
      {{ option.label }}
    </n-radio-button>
  </n-radio-group>
</template>
