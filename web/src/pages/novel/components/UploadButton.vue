<script lang="ts" setup>
import { PlusOutlined } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  UploadInst,
} from 'naive-ui';

import { Locator, formatError } from '@/data';
import { useWenkuNovelStore } from '../WenkuNovelStore';
import { RegexUtil } from '@/util';
import { getFullContent } from '@/util/file';

const props = defineProps<{
  novelId: string;
  allowZh: boolean;
}>();

const message = useMessage();

const { isSignedIn } = Locator.authRepository();

const store = useWenkuNovelStore(props.novelId);

async function beforeUpload({ file }: { file: UploadFileInfo }) {
  if (!isSignedIn.value) {
    message.info('请先登录');
    return false;
  }
  if (!file.file) {
    return false;
  }
  if (
    ['jp', 'zh', 'zh-jp', 'jp-zh'].some((prefix) =>
      file.file!!.name.startsWith(prefix),
    )
  ) {
    message.error('不要上传本网站上生成的机翻文件');
    return false;
  }
  if (file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过40MB');
    return false;
  }

  const content = await getFullContent(file.file);
  const charsCount = RegexUtil.countLanguageCharacters(content);
  if (charsCount.total < 500) {
    message.error('字数过少，请检查内容是不是图片');
    return false;
  }

  const p = (charsCount.jp + charsCount.ko) / charsCount.total;
  if (p < 0.33) {
    if (!props.allowZh) {
      message.error('疑似中文小说，文库不允许上传');
      return false;
    } else {
      file.url = 'zh';
    }
  } else {
    file.url = 'jp';
  }
}

const customRequest = async ({
  file,
  onFinish,
  onError,
  onProgress,
}: UploadCustomRequestOptions) => {
  if (!isSignedIn) {
    onError();
    return;
  }

  try {
    const type = file.url === 'jp' ? 'jp' : 'zh';
    await store.createVolume(file.name, type, file.file as File, (percent) =>
      onProgress({ percent }),
    );
    onFinish();
  } catch (e) {
    onError();
    message.error(`上传失败:${await formatError(e)}`);
  }
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
        日文章节上传前请确定里面有文本，单卷书压缩包超40MB里面大概率只有扫图无文本，这种是无法翻译的。
      </n-li>
      <n-li>EPUB文件大小超过40MB无法上传，请压缩里面的插图。</n-li>
      <n-li>不要上传已存在的分卷，现存的分卷有问题请联系管理员。</n-li>
      <n-li>分卷文件名应当只包含日文标题、卷数、分卷日文标题。</n-li>
    </n-ul>
    <n-p> 由于文库小说还在开发中，规则也会变化，务必留意。 </n-p>

    <template #action>
      <c-button label="确定" type="primary" @action="showRuleModal = false" />
    </template>
  </c-modal>
</template>
