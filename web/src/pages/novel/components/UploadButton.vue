<script lang="ts" setup>
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { PlusOutlined } from '@vicons/material';

import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/user_data';

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
  emits('uploadFinished');
}

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return false;
  }
  if (
    file.file &&
    ['jp', 'zh', 'zh-jp', 'jp-zh'].some((prefix) =>
      file.file!!.name.startsWith(prefix)
    )
  ) {
    message.error('不要上传本网站上生成的机翻文件');
    return false;
  }
  if (file.file && file.file.size > 1024 * 1024 * 40) {
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
  if (userData.token === undefined) {
    onError();
    return;
  }
  ApiWenkuNovel.createVolume(
    novelId,
    file.name,
    type,
    file.file as File,
    userData.token,
    (p) => onProgress({ percent: p })
  ).then((result) => {
    if (result.ok) {
      onFinish();
    } else {
      message.error(`上传失败:${result.error.message}`);
      onError();
    }
  });
};
</script>

<template>
  <n-upload
    multiple
    :custom-request="customRequest"
    @finish="onFinish"
    @before-upload="beforeUpload"
  >
    <c-button label="上传章节" :icon="PlusOutlined" />
  </n-upload>
</template>