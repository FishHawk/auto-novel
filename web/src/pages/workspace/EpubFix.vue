<script lang="ts" setup>
import { downloadFile } from '@/util';
import { EpubTool } from '@/util/file/epubfix';
import { DriveFolderUploadOutlined } from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';
import { onUpdated, ref } from 'vue';
import path from 'path';

const message = useMessage();
const logs = ref<{ id: number; type: string; message: string }[]>([]);
const logContainerRef = ref<HTMLElement | null>();

interface LoadedVolume {
  filename: string;
  file: File;
  blob: Blob;
}

const loadedVolumes = ref<LoadedVolume[]>([]);

const loadVolume = async (filename: string, file: File) => {
  if (
    loadedVolumes.value.find((it) => it.filename === filename) !== undefined
  ) {
    message.warning('文件已经载入');
    return;
  }
  const blob = await rebuild(file);
  if (!blob) {
    message.error('EPUB修复失败');
    return;
  }
  message.success('EPUB修复成功');
  loadedVolumes.value.push({
    filename,
    file,
    blob,
  });
  scrollToBottom();
};

const rebuildVolume = async (volume: LoadedVolume) => {
  const blob = await rebuild(volume.file);
  if (!blob) {
    message.error('EPUB修复失败');
    return;
  }
  volume.blob = blob;
  message.success('EPUB修复成功');
  scrollToBottom();
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

const download = async (volume: LoadedVolume) => {
  downloadFile(volume.filename, volume.blob);
};

const addLog = (type: string, message: string) => {
  logs.value.push({ id: logs.value.length + 1, type, message });
};

const rebuild = async (epub_src: File): Promise<Blob> => {
  logs.value = [];
  addLog('info', `${epub_src.name} 正在尝试重构EPUB`);

  const epub = new EpubTool(epub_src.name, epub_src);
  const restructuredEpub = await epub.restructure(epub_src); // Ensure restructure is an asynchronous function

  let el = { ...epub.errorLink_log }; // Clone the errorLink_log object
  let del_keys: string[] = [];

  for (const file_path of Object.keys(epub.errorLink_log)) {
    const log = epub.errorLink_log[file_path];
    if (file_path.toLowerCase().endsWith('.css')) {
      el[file_path] = log.filter(
        ([_, correct_path]: [string, string | null]) => correct_path !== null,
      );
      if (el[file_path].length === 0) {
        del_keys.push(file_path);
      }
    }
  }
  for (const key of del_keys) {
    delete el[key];
  }
  if (epub.errorOPF_log.length > 0) {
    addLog('error', '-------在 OPF文件 发现问题------:');
    for (const [error_type, error_value] of epub.errorOPF_log) {
      if (error_type === 'duplicate_id') {
        addLog(
          'error',
          `问题：发现manifest节点内部存在重复ID ${error_value} !!!`,
        );
        addLog('info', '措施：已自动清除重复ID对应的manifest项。');
      } else if (error_type === 'invalid_idref') {
        addLog(
          'error',
          `问题：发现spine节点内部存在无效引用ID ${error_value} !!!`,
        );
        addLog(
          'info',
          '措施：请自行检查spine内的itemref节点并手动修改，确保引用的ID存在于manifest的item项。\n' +
            '      （大小写不一致也会导致引用无效。）\n',
        );
      } else if (error_type === 'xhtml_not_in_spine') {
        addLog(
          'error',
          `问题：发现ID为 ${error_value} 的文件manifest中登记为application/xhtml+xml类型，但不被spine节点的项所引用`,
        );
        addLog(
          'info',
          '措施：自行检查该文件是否需要被spine引用。部分阅读器中，如果存在xhtml文件不被spine引用，可能导致epub无法打开。\n',
        );
      }
    }
  }
  if (Object.keys(el).length > 0) {
    for (const file_path of Object.keys(el)) {
      const log = el[file_path];
      const basename = path.basename(file_path);
      addLog('error', `-----在 ${basename} 发现问题链接-----:`);
      for (const [href, correct_path] of log) {
        if (correct_path !== null) {
          addLog(
            'error',
            `链接：${href}\n问题：与实际文件名大小写不一致！\n措施：程序已自动纠正链接。\n`,
          );
        } else {
          addLog('error', `链接：${href}\n问题：未能找到对应文件！！！\n`);
        }
      }
    }
  }

  addLog('info', `${epub_src.name} 重构EPUB成功`);

  return restructuredEpub;
};

const scrollToBottom = () => {
  const logContainer = logContainerRef.value;
  if (logContainer) {
    logContainer.scrollTop = logContainer.scrollHeight; // Scroll to the bottom
  }
};
</script>

<template>
  <div class="layout-content">
    <n-h1>EPUB修复</n-h1>

    <bulletin>
      <n-p>
        EPUB文件可能存在一些问题，这会导致阅读器错误。你可以上传epub文件来修复这些问题。
      </n-p>
      <n-p> 当前的修复方法是检测并修复EPUB文件中的链接和ID问题。 </n-p>
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
          <div>拖拽文件到这里上传</div>
        </n-upload-dragger>
      </n-upload>
      <n-flex vertical>
        <div v-for="volume of loadedVolumes" :key="volume.filename">
          <n-button type="success" text @click="download(volume)">
            [点击下载修复版]
            {{ volume.filename }}
          </n-button>
          <n-button type="warning" text @click="rebuildVolume(volume)">
            [重新生成]
          </n-button>
        </div>
      </n-flex>
      <n-flex vertical>
        <n-h2>日志</n-h2>
        <div style="max-height: 300px; overflow-y: auto" ref="logContainerRef">
          <div v-for="log in logs" :key="log.id" :class="`log-${log.type}`">
            <n-p>{{ log.message }}</n-p>
          </div>
        </div>
      </n-flex>
    </n-flex>
  </div>
</template>
