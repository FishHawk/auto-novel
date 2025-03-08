<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';

import { Locator } from '@/data';
import { ParsedFile, parseFile } from '@/util/file';
import { downloadFile, downloadFilesPacked } from '@/util';

const message = useMessage();

const files = shallowRef<ParsedFile[]>([]);

const loadFile = async (file: File) => {
  if (files.value.find((it) => it.name === file.name) !== undefined) {
    message.warning('文件已经载入');
    return;
  }
  try {
    const toolboxFile = await parseFile(file, ['txt', 'epub']);
    files.value.push(toolboxFile);
    files.value = [...files.value];
    triggerRef(files);
  } catch (e) {
    message.warning(`${e}`);
  }
};

const removeFile = (name: string) => {
  files.value = files.value.filter((it) => !(it.name === name));
  triggerRef(files);
};

const clearFile = () => {
  files.value = [];
  triggerRef(files);
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

    <n-flex>
      <div>
        <n-upload
          :show-file-list="false"
          accept=".txt,.epub"
          multiple
          directory-dnd
          :custom-request="customRequest"
        >
          <c-button label="加载文件" :icon="PlusOutlined" />
        </n-upload>
      </div>

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
    </n-flex>

    <n-divider />

    <n-tabs type="segment" animated>
      <n-tab-pane name="0" tab="术语表">
        <toolbox-item-glossary :files="files" />
      </n-tab-pane>
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
