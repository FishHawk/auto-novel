<script lang="ts" setup>
import ArchiveOutlined from '@vicons/material/es/ArchiveOutlined';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { ref } from 'vue';

import AdvanceOptions from './components/AdvanceOptions.vue';

const message = useMessage();

const advanceOptions = ref<InstanceType<typeof AdvanceOptions>>();

interface Volume {
  volumeId: string;
  createAt: number;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  glossary: { [key: string]: string };
}

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

    <n-p>
      <b>
        这是全新的文件翻译版块，所有操作均在浏览器内完成，无需注册或登录。即使服务器发生故障，系统仍能正常运行。
        <br />
        旧版文件翻译仍可继续使用，但将逐步弃用。目前已无法上传新文件。
        <br />
        该板块处于测试阶段，难免遇到问题，欢迎反馈。
      </b>
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
          支持TXT/EPUB文件，文件将保存在你的浏览器数据里面
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
