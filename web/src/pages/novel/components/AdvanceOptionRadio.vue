<script lang="ts" setup>
defineProps<{
  value: string;
  options: { label: string; value: string }[];
}>();
defineEmits(['update:value']);
</script>

<template>
  <AdvanceOption>
    <n-radio-group :value="value" @update:value="$emit('update:value', $event)">
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
