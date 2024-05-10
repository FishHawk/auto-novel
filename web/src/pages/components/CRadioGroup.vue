<script lang="ts" setup>
defineProps<{
  options: { label: string; value: string | number }[];
}>();
</script>

<template>
  <n-radio-group>
    <n-radio-button
      v-for="option in options"
      :key="option.value"
      :value="option.value"
      :label="option.label"
    />
  </n-radio-group>
</template>
