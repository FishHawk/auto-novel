<script lang="ts" setup>
import {
  DeleteOutlineOutlined,
  DriveFolderUploadOutlined,
  PlusOutlined,
} from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';

import { Locator } from '@/data';
import { ParsedFile, parseFile } from '@/util/file';
import { downloadFile, downloadFilesPacked } from '@/util';

const message = useMessage();

const files = ref<ParsedFile[]>([]);

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

const download = async () => {
  if (files.value.length === 0) {
    message.info('未载入文件');
  } else if (files.value.length === 1) {
    const file = files.value[0];
    await downloadFile(file.name, await file.toBlob());
  } else {
    const filesToDownload: [string, Blob][] = [];
    for (const file of files.value) {
      filesToDownload.push([file.name, await file.toBlob()]);
    }
    await downloadFilesPacked(filesToDownload);
  }
};
</script>

<template>
  <div class="layout-content">
    <n-h1>小说工具箱</n-h1>

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

    <n-flex>
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
      <c-button label="下载" @action="download" />
    </n-flex>

    <n-flex vertical style="margin-top: 16px">
      <n-text v-for="file of files">
        <toolbox-file-card :file="file" @delete="removeFile(file.name)" />
      </n-text>
      <n-empty v-if="files.length === 0" description="未载入文件" />
    </n-flex>

    <n-divider />

    <n-tabs type="segment" animated>
      <n-tab-pane name="1" tab="EPUB：压缩图片">
        <toolbox-item-compress-image :files="files" />
      </n-tab-pane>
      <n-tab-pane name="2" tab="TXT：修复OCR换行">
        <toolbox-item-fix-ocr :files="files" />
      </n-tab-pane>
      <n-tab-pane name="3" tab="EPUB：转换成TXT">
        <toolbox-item-convert v-model:files="files" />
      </n-tab-pane>
    </n-tabs>

    <local-volume-list-katakana
      v-model:show="showListModal"
      @volume-loaded="loadLocalFile"
    />
  </div>
</template>
