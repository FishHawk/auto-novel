<script lang="ts" setup>
defineProps<{ value: boolean }>();
defineEmits(['update:value']);
</script>

<template>
  <AdvanceOption>
    <template #suffix>
      <n-switch
        :rubber-band="false"
        size="small"
        :value="value"
        @update:value="$emit('update:value', $event)"
      />
    </template>
  </AdvanceOption>
</template>
