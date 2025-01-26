<script lang="ts" setup>
import { downloadFile } from '@/util';
import { getFullContent } from '@/util/file';
import { DriveFolderUploadOutlined } from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';

const message = useMessage();

interface LoadedVolume {
  filename: string;
  content: string;
}

const loadedVolumes = ref<LoadedVolume[]>([]);

const loadVolume = async (filename: string, file: File) => {
  if (
    loadedVolumes.value.find((it) => it.filename === filename) !== undefined
  ) {
    message.warning('文件已经载入');
    return;
  }

  const content = await getFullContent(file);
  loadedVolumes.value.push({
    filename,
    content,
  });
};

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  if (!file.file) return;
  loadVolume(file.name, file.file)
    .then(onFinish)
    .catch((err) => {
      message.error('文件载入失败:' + err);
      onError();
    });
};

const downloadTxt = async (volume: LoadedVolume) => {
  downloadFile(
    volume.filename.replace(/\.epub$/, '.txt'),
    new Blob([volume.content], { type: 'text/plain' }),
  );
};
</script>

<template>
  <div class="layout-content">
    <n-h1>EPUB/TXT转换</n-h1>

    <bulletin>
      <n-p> 该工具支持将 EPUB 文件提取为 TXT 文本。 </n-p>
      <n-p> 请上传 EPUB 文件并点击对应按钮下载提取的文本内容。 </n-p>
    </bulletin>

    <n-flex vertical>
      <n-upload
        :show-file-list="false"
        accept=".epub"
        multiple
        directory-dnd
        :custom-request="customRequest"
      >
        <n-upload-dragger style="margin: 16px 0">
          <n-icon size="32" :component="DriveFolderUploadOutlined" />
          <div>拖拽 EPUB 文件到这里提取文本</div>
        </n-upload-dragger>
      </n-upload>

      <n-flex vertical>
        <div v-for="volume in loadedVolumes" :key="volume.filename">
          <n-button type="success" text @click="downloadTxt(volume)">
            [下载提取后的TXT]
            {{ volume.filename }}
          </n-button>
        </div>
      </n-flex>
    </n-flex>
  </div>
</template>
