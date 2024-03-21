<script lang="ts" setup>
defineProps<{ icon: Component; text: string }>();
</script>

<template>
  <n-button :focusable="false" style="height: 64px">
    <div>
      <n-icon size="24" :component="icon" />
      <br />
      {{ text }}
    </div>
  </n-button>
</template>
