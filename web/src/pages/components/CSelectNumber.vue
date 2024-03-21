<script lang="ts" setup>
import { PlusOutlined, MinusOutlined } from '@vicons/material';

const props = defineProps<{
  value: number;
  options: number[];
  format: (value: number) => string;
}>();

const emit = defineEmits<{ 'update:value': [number] }>();

const index = computed(() => props.options.indexOf(props.value));

const toggle = (delta: 1 | -1) => {
  const nextIndex = Math.min(
    Math.max(index.value + delta, 0),
    props.options.length - 1
  );
  emit('update:value', props.options[nextIndex]);
};
</script>

<template>
  <n-input-group>
    <n-button :disabled="index <= 0" @click="toggle(-1)">
      <n-icon :component="MinusOutlined" />
    </n-button>
    <n-input-group-label style="min-width: 57px; text-align: center">
      {{ format(value) }}
    </n-input-group-label>
    <n-button :disabled="index >= options.length - 1" @click="toggle(1)">
      <n-icon :component="PlusOutlined" />
    </n-button>
  </n-input-group>
</template>
