<script setup lang="ts">
import { DriveFolderUploadOutlined } from '@vicons/material';
import { useEventListener } from '@vueuse/core';

const showDropZone = ref(false);
const dragFlag = { isDragStart: false };

// 将文件从操作系统拖拽到浏览器内，不会触发 dragstart 和 dragend 事件
useEventListener(document, ['dragenter', 'dragstart', 'dragend'], (e) => {
  if (e.type === 'dragstart') {
    dragFlag.isDragStart = true;
  } else if (e.type === 'dragenter' && !dragFlag.isDragStart) {
    e.preventDefault();
    showDropZone.value = true;
  } else if (e.type === 'dragend') {
    dragFlag.isDragStart = false;
  }
});
const handleDragLeave = (e: DragEvent) => {
  e.preventDefault();
  showDropZone.value = false;
};
const handleDrop = (e: DragEvent) => {
  e.preventDefault();
  showDropZone.value = false;
};
</script>

<template>
  <teleport to="body">
    <div class="drop-zone-wrap" v-show="showDropZone">
      <n-upload
        v-bind="$attrs"
        :show-file-list="false"
        trigger-style="height:100%"
        class="drop-zone"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
      >
        <n-upload-dragger class="drop-zone-placeholder">
          <n-icon class="drop-icon" :component="DriveFolderUploadOutlined" />
          <div><slot></slot></div>
        </n-upload-dragger>
      </n-upload>
    </div>
  </teleport>
</template>

<style scoped>
.drop-zone-wrap {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.7);
  z-index: 2000;
  box-sizing: border-box;
}

.drop-zone {
  width: 100%;
  height: 100%;
  cursor: pointer;
  box-sizing: border-box;
}

.drop-zone-placeholder {
  pointer-events: none;
  position: fixed;
  left: 42px;
  top: 42px;
  right: 42px;
  bottom: 42px;
  width: auto;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  color: #fff;
  background-color: transparent;
  font-size: 24px;
  border-radius: 12px;
  border-width: 2px !important;
}

.drop-icon {
  font-size: 48px;
  margin-bottom: 16px;
}
</style>
