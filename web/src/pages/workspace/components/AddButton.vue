<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';

import { PersonalVolumesManager } from '@/data/translator';

const message = useMessage();

const beforeUpload = ({ file }: { file: UploadFileInfo }) => {
  if (!(file.name.endsWith('.txt') || file.name.endsWith('.epub'))) {
    message.error(
      `上传失败:不允许的文件类型，必须是EPUB或TXT文件\n文件名: ${file.name}`
    );
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
  PersonalVolumesManager.saveVolume(file.file!!)
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}\n文件名: ${file.name}`);
      onError();
    });
};
</script>

<template>
  <n-upload
    multiple
    directory-dnd
    :custom-request="customRequest"
    @before-upload="beforeUpload"
  >
    <c-button label="添加文件" :icon="PlusOutlined" />
  </n-upload>
</template>
