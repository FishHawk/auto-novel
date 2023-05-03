<script lang="ts" setup>
defineProps<{
  desktop: boolean;
  label: string;
  value: string;
  options: { label: string; value: string }[];
}>();

const emit = defineEmits<{
  (e: 'update:value', value: string): void;
}>();
</script>

<template>
  <tr>
    <td nowrap="nowrap" style="padding-right: 12px">
      {{ label }}
    </td>
    <td style="width: 100%">
      <slot>
        <n-radio-group
          v-if="desktop"
          :value="value"
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
          style="width: 100%"
        />
      </slot>
    </td>
  </tr>
</template>
