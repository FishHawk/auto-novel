<template>
  <n-modal
    preset="card"
    :bordered="false"
    :closable="$attrs.title !== undefined || $slots.header !== undefined"
    size="large"
    style="width: min(600px, calc(100% - 16px))"
  >
    <template #header v-if="$slots.header">
      <slot name="header" />
    </template>
    <n-scrollbar trigger="none" style="max-height: 60vh">
      <slot />
    </n-scrollbar>

    <template #action v-if="$slots.action">
      <n-flex justify="end">
        <slot name="action" />
      </n-flex>
    </template>
  </n-modal>
</template>
