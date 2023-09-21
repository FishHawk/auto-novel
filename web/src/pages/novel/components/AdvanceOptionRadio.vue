<script lang="ts" setup>
defineProps<{
  value: string;
  disabled: boolean;
  options: { label: string; value: string }[];
}>();
defineEmits(['update:value']);
</script>

<template>
  <AdvanceOption>
    <n-radio-group
      :value="value"
      @update:value="$emit('update:value', $event)"
      :disabled="disabled"
    >
      <n-radio
        v-for="option in options"
        :key="option.value"
        :value="option.value"
        size="small"
        style="font-size: 12px"
      >
        {{ option.label }}
      </n-radio>
    </n-radio-group>
    <slot />
  </AdvanceOption>
</template>
