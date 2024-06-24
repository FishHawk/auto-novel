<script lang="ts" setup>
import { DriveFolderUploadOutlined, PlusOutlined } from '@vicons/material';
import { useEventListener } from '@vueuse/core';
import { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';

import { useBookshelfLocalStore } from '../BookshelfLocalStore';

const props = defineProps<{ favoredId?: string }>();
const emit = defineEmits<{ done: [File] }>();

const message = useMessage();

const store = useBookshelfLocalStore();

const onFinish = ({ file }: { file: UploadFileInfo }) => {
  emit('done', file.file!!);
};

const beforeUpload = ({ file }: { file: UploadFileInfo }) => {
  if (
    !(
      file.name.endsWith('.txt') ||
      file.name.endsWith('.srt') ||
      file.name.endsWith('.epub')
    )
  ) {
    message.error(`上传失败:文件类型不允许\n文件名： ${file.name}`);
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error(`上传失败:文件大小不能超过40MB\n文件名: ${file.name}`);
    return false;
  }
};

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  store
    .addVolume(file.file!!, props.favoredId ?? 'default')
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}\n文件名: ${file.name}`);
      onError();
    });
};

const showDropZone = ref(false);
let dragFlag = { isDragStart: false };
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
  <n-upload
    :show-file-list="false"
    accept=".txt,.epub,.srt"
    multiple
    directory-dnd
    :custom-request="customRequest"
    @before-upload="beforeUpload"
    @finish="onFinish"
  >
    <n-tooltip trigger="hover">
      <template #trigger>
        <c-button label="添加" :icon="PlusOutlined" />
      </template>
      支持拖拽上传Epub/Txt/Srt文件
      <br />
      百度/有道/GPT支持韩语/英语小说
    </n-tooltip>
  </n-upload>

  <teleport to="body">
    <div class="drop-zone-wrap" v-show="showDropZone">
      <n-upload
        :show-file-list="false"
        @finish="onFinish"
        accept=".txt,.epub,.srt"
        multiple
        directory-dnd
        :custom-request="customRequest"
        @before-upload="beforeUpload"
        class="drop-zone"
        trigger-style="height:100%"
        @dragleave="handleDragLeave"
        @drop="handleDrop"
      >
        <n-upload-dragger class="drop-zone-placeholder">
          <n-icon class="drop-icon" :component="DriveFolderUploadOutlined" />
          <div>拖拽文件到这里上传</div>
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
