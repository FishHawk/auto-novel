<script lang="ts" setup>
defineProps<{
  label: string;
}>();
</script>
<template>
  <tr>
    <td nowrap="nowrap" style="vertical-align: top; padding-right: 20px">
      {{ label }}
    </td>
    <td><slot /></td>
  </tr>
</template>
