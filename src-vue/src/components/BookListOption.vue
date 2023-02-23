<script lang="ts" setup>
defineProps<{
  title: string;
  values: string[];
  selected: number;
}>();
defineEmits(['update:selected']);
</script>

<template>
  <tr>
    <td
      nowrap="nowrap"
      style="vertical-align: top; padding-right: 12px; color: grey"
    >
      {{ title }}
    </td>
    <td>
      <n-space size="large">
        <n-button
          v-for="(value, index) in values"
          text
          :type="index == selected ? 'primary' : 'default'"
          @click="$emit('update:selected', index)"
        >
          {{ value }}
        </n-button>
      </n-space>
    </td>
  </tr>
</template>
