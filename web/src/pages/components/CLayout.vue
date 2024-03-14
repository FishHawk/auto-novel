<script lang="ts" setup>
defineProps<{ sidebar: boolean; sidebarWidth: number }>();
</script>

<template>
  <n-flex :wrap="false" :size="20">
    <div style="flex: 1 1 auto; overflow: auto">
      <slot />
    </div>

    <template v-if="sidebar">
      <div style="flex: 0 0 1px">
        <n-divider
          vertical
          style="
            position: fixed;
            top: 50px;
            height: calc(100vh - 50px);
            margin: 0;
          "
        />
      </div>

      <div :style="{ flex: `0 0 ${sidebarWidth}px` }">
        <n-flex
          :size="0"
          vertical
          style="position: fixed; top: 50px; height: calc(100vh - 50px)"
          :style="{ width: `${sidebarWidth}px` }"
        >
          <slot name="sidebar" />
        </n-flex>
      </div>
    </template>
  </n-flex>
</template>
