<script lang="ts">
export const notice = (text: string, alert?: boolean) => ({
  text,
  alert: alert ?? false,
});
</script>

<script lang="ts" setup>
interface Notice {
  text: string;
  alert: boolean;
}

defineProps<{ notices: Notice[] }>();
</script>

<template>
  <n-card embedded>
    <slot />
    <n-p
      v-for="n of notices"
      style="margin-top: 0.5em; margin-bottom: 0em"
      :style="n.alert ? { color: 'red' } : {}"
    >
      {{ n.text }}
    </n-p>
  </n-card>
</template>
