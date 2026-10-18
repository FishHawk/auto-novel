<script lang="ts" setup>
import { Toast, XTooltip } from '@novelia/web-kit';
import { PlusOutlined } from '@vicons/material';
import type { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';

import { useBookshelfLocalStore } from '../BookshelfLocalStore';

const props = defineProps<{
  favoredId?: string;
}>();
const emit = defineEmits<{
  done: [File];
}>();

const store = useBookshelfLocalStore();

const onFinish = ({ file }: { file: UploadFileInfo }) => {
  emit('done', file.file!);
};

const validateFile = (file: File) => {
  if (
    !(
      file.name.endsWith('.txt') ||
      file.name.endsWith('.srt') ||
      file.name.endsWith('.epub')
    )
  ) {
    Toast.error(`上传失败:文件类型不允许\n文件名： ${file.name}`);
    return false;
  }
  if (file.size > 1024 * 1024 * 100) {
    Toast.error(`上传失败:文件大小不能超过100MB\n文件名: ${file.name}`);
    return false;
  }
  return true;
};

const beforeUpload = ({ file }: { file: UploadFileInfo }) =>
  file.file != null && validateFile(file.file);

const uploadFile = (file: File) =>
  store
    .addVolume(file, props.favoredId ?? 'default')
    .catch((error: unknown) => {
      Toast.error(`上传失败:${error}\n文件名: ${file.name}`);
      throw error;
    });

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  void uploadFile(file.file!).then(onFinish).catch(onError);
};

const handleDrop = (files: File[]) => {
  for (const file of files) {
    if (!validateFile(file)) continue;
    void uploadFile(file)
      .then(() => emit('done', file))
      .catch(() => {
        // uploadFile 已显示与按钮上传相同的失败提示。
      });
  }
};

const handleDropError = (error: unknown) => {
  Toast.error(`上传失败:读取拖拽目录失败\n${error}`);
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
    <XTooltip>
      <template #trigger>
        <c-button label="添加" :icon="PlusOutlined" />
      </template>
      支持拖拽上传Epub/Txt/Srt文件
      <br />
      有道/GPT支持韩语/英语小说
    </XTooltip>
  </n-upload>
  <DropZone @drop="handleDrop" @error="handleDropError">
    拖拽文件到这里上传
  </DropZone>
</template>
