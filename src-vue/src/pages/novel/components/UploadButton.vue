<script lang="ts" setup>
import { UploadFileInfo, useMessage } from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const { novelId, type } = defineProps<{
  novelId: string;
  type: 'jp' | 'zh';
}>();

const emits = defineEmits<{ uploadFinished: [] }>();

const authInfoStore = useAuthInfoStore();
const message = useMessage();

function handleFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  emits('uploadFinished');
  return undefined;
}

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!authInfoStore.token) {
    message.info('请先登录');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过40MB');
    return false;
  }
}

function createUploadUrl(novelId: string): string {
  if (type === 'jp') {
    return ApiWenkuNovel.createVolumeJpUploadUrl(novelId);
  } else {
    return ApiWenkuNovel.createVolumeZhUploadUrl(novelId);
  }
}
</script>

<template>
  <n-upload
    multiple
    :headers="{ Authorization: 'Bearer ' + authInfoStore.token }"
    :action="createUploadUrl(novelId)"
    @finish="handleFinish"
    @before-upload="beforeUpload"
  >
    <n-button>
      <template #icon><n-icon :component="UploadFilled" /></template>
      上传章节
    </n-button>
  </n-upload>
</template>
