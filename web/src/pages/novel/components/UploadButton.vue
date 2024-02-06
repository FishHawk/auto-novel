<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  UploadInst,
  useMessage,
} from 'naive-ui';
import { computed, ref } from 'vue';

import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { useReadStateStore } from '@/data/stores/read_states';
import { useUserDataStore } from '@/data/stores/user_data';

const { novelId, type } = defineProps<{
  novelId: string;
  type: 'jp' | 'zh';
}>();

const emits = defineEmits<{ uploadFinished: [] }>();

const userData = useUserDataStore();
const readState = useReadStateStore();
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

const showRuleModal = ref(false);
const haveReadRule = computed(() => {
  const durationSinceLastRead = Date.now() - readState.wenkuUploadRule;
  return durationSinceLastRead < 24 * 3600 * 1000;
});
const uploadRef = ref<UploadInst>();
const uploadVolumes = () => {
  showRuleModal.value = true;
  readState.wenkuUploadRule = Date.now();
};
</script>

<template>
  <c-button
    v-if="!haveReadRule"
    label="上传"
    :icon="PlusOutlined"
    @click="uploadVolumes"
  />
  <n-upload
    ref="uploadRef"
    multiple
    :custom-request="customRequest"
    :show-trigger="haveReadRule"
    @finish="onFinish"
    @before-upload="beforeUpload"
  >
    <c-button label="上传" :icon="PlusOutlined" />
  </n-upload>

  <c-modal
    title="上传须知"
    v-model:show="showRuleModal"
    @after-leave="uploadRef?.openOpenFileDialog()"
  >
    <n-p> 在上传小说之前，请务必遵守以下规则。 </n-p>
    <n-ul>
      <n-li>不要上传机翻，请上传日文文件并生成机翻。</n-li>
      <n-li>日文章节不要上传全是图片的EPUB文件，上传了也翻译不了。</n-li>
    </n-ul>
    <n-p> 由于文库小说还在开发中，规则也会变化，务必留意。 </n-p>

    <template #action>
      <c-button label="确定" type="primary" @click="showRuleModal = false" />
    </template>
  </c-modal>
</template>
