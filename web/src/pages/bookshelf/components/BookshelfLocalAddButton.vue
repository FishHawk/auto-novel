<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import { UploadCustomRequestOptions, UploadFileInfo } from 'naive-ui';

import { useBookshelfLocalStore } from '../BookshelfLocalStore';

const props = defineProps<{
  favoredId?: string;
}>();
const emit = defineEmits<{
  done: [File];
}>();

const message = useMessage();

const store = useBookshelfLocalStore();

const onFinish = ({ file }: { file: UploadFileInfo }) => {
  emit('done', file.file!);
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
  if (file.file?.size && file.file.size > 1024 * 1024 * 100) {
    message.error(`上传失败:文件大小不能超过100MB\n文件名: ${file.name}`);
    return false;
  }
};

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  store
    .addVolume(file.file!, props.favoredId ?? 'default')
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}\n文件名: ${file.name}`);
      onError();
    });
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
  <DropZone
    @finish="onFinish"
    accept=".txt,.epub,.srt"
    multiple
    directory-dnd
    :custom-request="customRequest"
    @before-upload="beforeUpload"
  >
    拖拽文件到这里上传
  </DropZone>
</template>
