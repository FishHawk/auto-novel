<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  DriveFolderUploadOutlined,
  PlusOutlined,
} from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';

import { Locator } from '@/data';
import { Epub, parseFile, Srt, Txt } from '@/util/file';
import { downloadFile, downloadFilesPacked } from '@/util';

import { Toolbox } from './Toolbox';

const message = useMessage();

type ToolboxFile = Epub | Txt | Srt;

const files = ref<ToolboxFile[]>([]);
const epubFiles = computed(() => files.value.filter((e) => e instanceof Epub));
const txtFiles = computed(() => files.value.filter((e) => e instanceof Txt));

const loadFile = async (file: File) => {
  if (files.value.find((it) => it.name === file.name) !== undefined) {
    message.warning('文件已经载入');
    return;
  }
  try {
    const toolboxFile = await parseFile(file, ['txt', 'epub']);
    files.value.push(toolboxFile);
  } catch (e) {
    message.warning(`${e}`);
  }
};

const removeFile = (name: string) => {
  files.value = files.value.filter((it) => !(it.name === name));
};

const clearFile = () => {
  files.value = [];
};

const loadLocalFile = (volumeId: string) =>
  Locator.localVolumeRepository()
    .then((repo) => repo.getFile(volumeId))
    .then((file) => {
      if (file === undefined) throw '小说不存在';
      return loadFile(file.file);
    })
    .catch((error) => message.error(`文件载入失败：${error}`));

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  if (!file.file) return;
  loadFile(file.file)
    .then(onFinish)
    .catch((err) => {
      message.error('文件载入失败:' + err);
      onError();
    });
};

const showListModal = ref(false);

const fixOcr = () => Toolbox.fixOcr(files.value);

const convertToTxt = () => (files.value = Toolbox.convertToTxt(files.value));

const download = async (files: ToolboxFile[]) => {
  if (files.length === 0) {
    message.info('未载入文件');
  } else if (files.length === 1) {
    const file = files[0];
    await downloadFile(file.name, await file.toBlob());
  } else {
    const filesToDownload: [string, Blob][] = [];
    for (const file of files) {
      filesToDownload.push([file.name, await file.toBlob()]);
    }
    await downloadFilesPacked(filesToDownload);
  }
};
</script>

<template>
  <div class="layout-content">
    <n-h1>小说工具箱</n-h1>
    <bulletin>
      <n-p>TXT工具</n-p>
      <n-p
        ><b>修复OCR换行</b
        >：OCR输出的文本通常存在额外的换行符，导致翻译器错误。当前修复方法是检测每一行的结尾是否是字符（汉字/日文假名/韩文字符/英文字母），如果是的话则删除行尾的换行符。</n-p
      >
      <n-p>　</n-p>
      <n-p>EPUB工具</n-p>
      <n-p
        ><b>图片压缩</b
        >：压缩Epub内的图片，可以选择压缩格式、压缩质量、图片缩小比例、不压缩的图片</n-p
      >
    </bulletin>

    <section-header title="文件列表">
      <c-button
        label="加载本地小说"
        :icon="PlusOutlined"
        @action="showListModal = true"
      />
      <c-button
        label="清空"
        :icon="DeleteOutlineOutlined"
        @action="clearFile"
      />
    </section-header>

    <n-upload
      :show-file-list="false"
      accept=".txt,.epub"
      multiple
      directory-dnd
      :custom-request="customRequest"
    >
      <n-upload-dragger style="margin: 16px 0">
        <n-icon size="32" :component="DriveFolderUploadOutlined" />
        <div>拖拽文件到这里加载进工作区，支持TXT、EPUB格式</div>
      </n-upload-dragger>
    </n-upload>

    <div v-if="txtFiles.length > 0">
      <n-h2>TXT</n-h2>
      <n-list>
        <n-list-item v-for="file of txtFiles">
          <toolbox-file-card :file="file" @delete="removeFile(file.name)" />
        </n-list-item>
      </n-list>
      <c-action-wrapper title="批量处理">
        <n-flex vertical style="margin: 20px 0">
          <n-flex style="margin-bottom: 8px">
            <c-button label="修复OCR换行" size="small" @action="fixOcr" />
            <c-button label="下载" size="small" @action="download(txtFiles)" />
          </n-flex>
        </n-flex>
      </c-action-wrapper>
    </div>

    <div v-if="epubFiles.length > 0">
      <n-h2>EPUB</n-h2>
      <n-list>
        <n-list-item v-for="file of epubFiles">
          <toolbox-file-card :file="file" @delete="removeFile(file.name)" />
        </n-list-item>
      </n-list>
      <c-action-wrapper title="批量处理">
        <n-flex vertical style="margin: 20px 0">
          <n-flex>
            <c-button label="转换成TXT" size="small" @action="convertToTxt" />
            <c-button label="下载" size="small" @action="download(epubFiles)" />
          </n-flex>
        </n-flex>
      </c-action-wrapper>
    </div>

    <n-empty v-if="files.length === 0" description="未载入文件" />

    <c-drawer-right v-model:show="showListModal" title="本地小说">
      <div style="padding: 24px 16px">
        <local-volume-list-katakana hide-title @volume-loaded="loadLocalFile" />
      </div>
    </c-drawer-right>
  </div>
</template>
