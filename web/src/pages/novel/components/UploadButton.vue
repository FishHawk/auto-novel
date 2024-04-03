<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  UploadInst,
} from 'naive-ui';

import { WenkuNovelRepository } from '@/data/api';
import { useUserDataStore } from '@/data/stores/user_data';
import { formatError } from '@/data/api/client';
import { Locator } from '@/data';

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
  WenkuNovelRepository.createVolume(
    novelId,
    file.name,
    type,
    file.file as File,
    userData.token,
    (p) => onProgress({ percent: p })
  )
    .then(() => {
      onFinish();
    })
    .catch(async (e) => {
      onError();
      message.error(`上传失败:${await formatError(e)}`);
    });
};

const ruleViewed = Locator.ruleViewedRepository().ref;
const showRuleModal = ref(false);
const haveReadRule = computed(() => {
  const durationSinceLastRead = Date.now() - ruleViewed.value.wenkuUploadRule;
  return durationSinceLastRead < 24 * 3600 * 1000;
});
const uploadRef = ref<UploadInst>();
const uploadVolumes = () => {
  showRuleModal.value = true;
  ruleViewed.value.wenkuUploadRule = Date.now();
};
</script>

<template>
  <c-button
    v-if="!haveReadRule"
    label="上传"
    :icon="PlusOutlined"
    @action="uploadVolumes"
  />
  <n-upload
    ref="uploadRef"
    accept=".txt,.epub"
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
      <n-li>
        无论是中文章节还是日文章节，都不要上传机翻，你可以上传日文文件并生成机翻。
      </n-li>
      <n-li>
        日文章节上传前请确定里面有文本，单卷书压缩包超40MB里面大概率只有扫图无文本，这种是无法翻译的。
      </n-li>
      <n-li>EPUB文件大小超过40MB无法上传，请压缩里面的插图。</n-li>
      <n-li>文件名长度过长会无法上传，请删除副标题或其他附加信息。</n-li>
    </n-ul>
    <n-p> 由于文库小说还在开发中，规则也会变化，务必留意。 </n-p>

    <template #action>
      <c-button label="确定" type="primary" @action="showRuleModal = false" />
    </template>
  </c-modal>
</template>
