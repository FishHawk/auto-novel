<script lang="ts" setup>
withDefaults(
  defineProps<{
    label?: string;
    tags: string[];
    closable: boolean;
  }>(),
  { closable: false }
);

const emit = defineEmits<{
  (e: 'close', tag: string): void;
}>();
</script>

<template>
  <tr>
    <td
      v-if="label"
      nowrap="nowrap"
      style="vertical-align: top; padding-right: 12px"
    >
      <n-tag :bordered="false" size="small">
        {{ label }}
      </n-tag>
    </td>
    <td>
      <n-space :size="[4, 4]">
        <n-tag
          v-for="tag of tags"
          :bordered="false"
          size="small"
          :closable="closable"
          @close="emit('close', tag)"
        >
          {{ tag }}
        </n-tag>
      </n-space>
    </td>
  </tr>
</template>
