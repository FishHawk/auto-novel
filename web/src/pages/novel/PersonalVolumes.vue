<script lang="ts" setup>
import ArchiveOutlined from '@vicons/material/es/ArchiveOutlined';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { ref } from 'vue';

import AdvanceOptions from './components/AdvanceOptions.vue';
import { Volume } from './components/PersonalVolume.vue';

const message = useMessage();

const advanceOptions = ref<InstanceType<typeof AdvanceOptions>>();

const volumes = ref<Volume[]>([]);

const loadVolumes = () => {
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.listVolumes())
    .then((rawVolumes) => {
      volumes.value = rawVolumes.map((it) => {
        return {
          volumeId: it.id,
          createAt: it.createAt,
          total: it.toc.length,
          baidu: it.toc.filter((it) => it.baidu).length,
          youdao: it.toc.filter((it) => it.youdao).length,
          gpt: it.toc.filter((it) => it.gpt).length,
          sakura: it.toc.filter((it) => it.sakura).length,
          glossary: it.glossary,
        };
      });
    });
};
loadVolumes();

const beforeUpload = ({ file }: { file: UploadFileInfo }) => {
  if (!(file.name.endsWith('.txt') || file.name.endsWith('.epub'))) {
    message.error('不允许的文件类型，必须是EPUB或TXT文件');
    return false;
  }
  if (file.file?.size && file.file.size > 1024 * 1024 * 40) {
    message.error('文件大小不能超过40MB');
    return false;
  }
};

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.saveVolume(file.file!!))
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}`);
      onError();
    });
};

const showClearModal = ref(false);
const deleteAllVolumes = () => {
  return import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.deleteVolumesDb())
    .then(loadVolumes)
    .then(() => (showClearModal.value = false))
    .catch((error) => {
      message.error(`清空失败:${error}`);
    });
};
</script>

<template>
  <div class="layout-content">
    <n-h1>文件翻译</n-h1>

    <n-p style="color: red; font-size: 16px">
      1月18日星期四起，网站会开放sakura0.9版本上传，并禁止0.8版本上传。
      旧的0.8版本翻译会和术语表变化一样，直接过期。 0.9/0.8的翻译质量对比参见
      <n-a href="https://github.com/FishHawk/sakura-test" target="_blank">
        对比报告
      </n-a>
      。如果不满意，请提前备份小说。
    </n-p>

    <n-upload
      multiple
      directory-dnd
      :max="5"
      :custom-request="customRequest"
      @finish="loadVolumes"
      @before-upload="beforeUpload"
      style="margin-bottom: 32px"
    >
      <n-upload-dragger>
        <div style="margin-bottom: 12px">
          <n-icon size="48" :depth="3" :component="ArchiveOutlined" />
        </div>
        <n-text style="font-size: 16px">
          点击或者拖动文件到该区域来加载文件
        </n-text>
        <n-p depth="3" style="margin: 8px 0 0 0">
          支持TXT/EPUB文件，文件和翻译进度将保存在你的浏览器里面
        </n-p>
      </n-upload-dragger>
    </n-upload>

    <advance-options
      ref="advanceOptions"
      type="personal"
      :glossary="{}"
      :submit="async () => {}"
    >
      <n-button @click="showClearModal = true">清空所有章节</n-button>
    </advance-options>

    <n-list>
      <template v-for="volume of volumes">
        <n-list-item>
          <personal-volume
            :volume="volume"
            :get-params="() => advanceOptions!!.getTranslationOptions()"
            @require-refresh="loadVolumes"
          />
        </n-list-item>
      </template>
    </n-list>
  </div>

  <card-modal title="清空所有文件" v-model:show="showClearModal">
    <n-p>
      这将清空你的浏览器里面保存的所有EPUB/TXT文件，包括已经翻译的章节和术语表，无法恢复。
      你确定吗？
    </n-p>

    <template #action>
      <async-button type="primary" @async-click="deleteAllVolumes">
        确定
      </async-button>
      <n-button @click="showClearModal = false"> 取消 </n-button>
    </template>
  </card-modal>
</template>
