<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';

import { LocalVolumeRepository } from '@/data/local';

const message = useMessage();

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
  LocalVolumeRepository.createVolume(file.file!!)
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}\n文件名: ${file.name}`);
      onError();
    });
};
</script>

<template>
  <n-upload
    accept=".txt,.epub,.srt"
    multiple
    directory-dnd
    :custom-request="customRequest"
    @before-upload="beforeUpload"
  >
    <c-button label="添加文件" :icon="PlusOutlined" />
  </n-upload>
</template>
