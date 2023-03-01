<script lang="ts" setup>
defineProps<{
  token: { titleJp: string; titleZh?: string };
}>();
</script>

<template>
  <td style="width: 50%">
    {{ token.titleJp }}
    <br />
    <span class="on-mobile" style="color: grey">{{ token.titleZh }}</span>
  </td>
  <td class="on-desktop" style="color: grey">{{ token.titleZh }}</td>
</template>

<style scoped>
td {
  padding: 4px;
}
</style>
