<script lang="ts" setup>
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { UploadFilled } from '@vicons/material';

import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/userData';

const { novelId, type } = defineProps<{
  novelId: string;
  type: 'jp' | 'zh';
}>();

const emits = defineEmits<{ uploadFinished: [] }>();

const userData = useUserDataStore();
const message = useMessage();

function onFinish({
  file,
  event,
}: {
  file: UploadFileInfo;
  event?: ProgressEvent;
}) {
  file.status = 'removed';
  emits('uploadFinished');
  return file;
}

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过40MB');
    return false;
  }
}

const customRequest = ({
  file,
  onFinish,
  onError,
  onProgress,
}: UploadCustomRequestOptions) => {
  const formData = new FormData();
  formData.append(file.name, file.file as File);

  var xhr = new XMLHttpRequest();

  xhr.open(
    'POST',
    type === 'jp'
      ? ApiWenkuNovel.createVolumeJpUploadUrl(novelId, file.name)
      : ApiWenkuNovel.createVolumeZhUploadUrl(novelId, file.name)
  );

  xhr.setRequestHeader('Authorization', 'Bearer ' + userData.token);
  xhr.onload = function () {
    if (xhr.status === 200) {
      onFinish();
    } else {
      message.error(`上传失败:${xhr.responseText}`);
      onError();
    }
  };
  xhr.upload.addEventListener('progress', (e) => {
    const percent = e.lengthComputable ? (e.loaded / e.total) * 100 : 0;
    onProgress({ percent: Math.ceil(percent) });
  });
  xhr.send(formData);
};
</script>

<template>
  <n-upload
    multiple
    :custom-request="customRequest"
    @finish="onFinish"
    @before-upload="beforeUpload"
  >
    <n-button>
      <template #icon><n-icon :component="UploadFilled" /></template>
      上传章节
    </n-button>
  </n-upload>
</template>
