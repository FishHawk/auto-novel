<template>
  <n-modal
    preset="card"
    :bordered="false"
    :closable="$attrs.title !== undefined"
    size="large"
    style="width: min(600px, calc(100% - 16px))"
  >
    <n-scrollbar trigger="none" style="max-height: 60vh">
      <slot />
    </n-scrollbar>

    <template #action v-if="$slots.action">
      <n-space justify="end">
        <slot name="action" />
      </n-space>
    </template>
  </n-modal>
</template>
