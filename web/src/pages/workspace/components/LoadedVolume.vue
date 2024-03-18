<script lang="ts" setup>
import { DeleteOutlineOutlined } from '@vicons/material';
import { ref } from 'vue';

defineProps<{
  volume: {
    source: 'tmp' | 'local';
    filename: string;
    content: string;
  };
}>();

const emit = defineEmits<{ delete: [] }>();

const showPreviewModal = ref(false);
</script>

<template>
  <n-flex :size="4" aign="center" style="font-size: 12px" :wrap="false">
    <c-icon-button
      tooltip="预览"
      :icon="DeleteOutlineOutlined"
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
    <n-text>
      {{ volume.source === 'tmp' ? '临时文件' : '本地文件' }}
      /
      {{ volume.filename }}
    </n-text>
  </n-flex>

  <c-modal title="预览（前100行）" v-model:show="showPreviewModal">
    <n-p v-for="line of volume.content.split('\n').slice(0, 100)">
      {{ line }}
    </n-p>
  </c-modal>
</template>
