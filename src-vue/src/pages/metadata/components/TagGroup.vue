<script lang="ts" setup>
defineProps<{
  label: string;
  tags: string[];
}>();
</script>

<template>
  <tr>
    <td nowrap="nowrap" style="vertical-align: top; padding-right: 12px">
      <n-tag :bordered="false" size="small">
        {{ label }}
      </n-tag>
    </td>
    <td>
      <n-space :size="[4, 4]">
        <n-tag v-for="tag of tags" :bordered="false" size="small">
          {{ tag }}
        </n-tag>
      </n-space>
    </td>
  </tr>
</template>
