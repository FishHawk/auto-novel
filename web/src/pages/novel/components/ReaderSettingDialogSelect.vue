<script lang="ts" setup>
withDefaults(
  defineProps<{
    desktop: boolean;
    value: string;
    disabled: boolean;
    options: { label: string; value: string }[];
  }>(),
  { disabled: false }
);
const emit = defineEmits<{
  (e: 'update:value', value: string): void;
}>();
</script>

<template>
  <n-radio-group
    v-if="desktop"
    :value="value"
    :disabled="disabled"
    @update:value="emit('update:value', $event)"
  >
    <n-space>
      <n-radio
        v-for="option in options"
        :key="option.value"
        :value="option.value"
      >
        {{ option.label }}
      </n-radio>
    </n-space>
  </n-radio-group>

  <n-select
    v-else
    :value="value"
    @update:value="emit('update:value', $event)"
    :options="options"
    :disabled="disabled"
    style="width: 100%"
  />
</template>
