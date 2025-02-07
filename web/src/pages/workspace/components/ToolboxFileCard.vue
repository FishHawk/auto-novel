<script lang="ts" setup>
import { DeleteOutlineOutlined, RemoveRedEyeOutlined } from '@vicons/material';

defineProps<{
  name: string;
}>();

const emit = defineEmits<{
  delete: [];
}>();

const showPreviewModal = ref(false);
</script>

<template>
  <n-flex :size="4" aign="center" style="font-size: 12px" :wrap="false">
    <c-icon-button
      tooltip="预览"
      :icon="RemoveRedEyeOutlined"
      text
      size="small"
      type="primary"
      @action="showPreviewModal = true"
    />
    <c-icon-button
      tooltip="移除"
      :icon="DeleteOutlineOutlined"
      text
      size="small"
      type="error"
      @action="emit('delete')"
    />
    <n-text>{{ name }}</n-text>
  </n-flex>

  <c-modal title="预览（前100行）" v-model:show="showPreviewModal">
    <n-text>暂未实现</n-text>
  </c-modal>
</template>
