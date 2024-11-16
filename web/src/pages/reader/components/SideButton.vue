<script lang="ts" setup>
const props = defineProps<{
  icon: Component;
  text: string;
  disable?: boolean;
}>();
</script>

<template>
  <n-button :focusable="false" :disabled="props.disable" style="height: 64px">
    <div>
      <n-icon size="24" :component="icon" style="margin-bottom: 4px" />
      <br />
      {{ text }}
    </div>
  </n-button>
</template>
