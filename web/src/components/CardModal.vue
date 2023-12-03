<template>
  <n-modal
    preset="card"
    :bordered="false"
    :closable="false"
    size="large"
    style="width: min(600px, calc(100% - 16px))"
  >
    <n-scrollbar trigger="none" style="max-height: 65vh">
      <slot />
    </n-scrollbar>

    <template #action v-if="$slots.action">
      <n-space justify="end">
        <slot name="action" />
      </n-space>
    </template>
  </n-modal>
</template>
