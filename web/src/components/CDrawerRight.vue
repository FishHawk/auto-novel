<script lang="ts" setup>
defineProps<{ title: string }>();
</script>

<template>
  <n-drawer
    :width="280"
    :auto-focus="false"
    :block-scroll="false"
    placement="right"
  >
    <n-drawer-content
      :native-scrollbar="false"
      :scrollbar-props="{ trigger: 'none' }"
    >
      <template #header>
        <n-flex align="center" justify="space-between">
          {{ title }}
          <slot name="action" />
        </n-flex>
      </template>
      <slot />
    </n-drawer-content>
  </n-drawer>
</template>
