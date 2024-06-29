<script lang="ts" setup>
import { downloadFile, RegexUtil } from '@/util';
import { Txt } from '@/util/file';
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

  let content: string;
  if (filename.endsWith('.txt')) {
    content = await Txt.readContent(file);
  } else {
    return;
  }

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

const fix = (volume: LoadedVolume) => {
  const endsCorrectly = (s: string) => {
    if (s.length === 0) {
      return true;
    }
    const lastChar = s.charAt(s.length - 1);
    if (
      RegexUtil.hasHanzi(lastChar) ||
      RegexUtil.hasKanaChars(lastChar) ||
      RegexUtil.hasHangulChars(lastChar) ||
      RegexUtil.hasEnglishChars(lastChar)
    ) {
      return false;
    } else {
      return true;
    }
  };

  const lines: string[] = [];
  let lineProcessing = '';
  for (let line of volume.content.split('\n')) {
    if (lineProcessing.length > 0) {
      line = lineProcessing + line.trim();
      lineProcessing = '';
    } else {
      line = line.trimEnd();
    }
    if (endsCorrectly(line)) {
      lines.push(line);
    } else {
      lineProcessing = line;
    }
  }
  if (lineProcessing.length > 0) {
    lines.push(lineProcessing);
  }

  const blob = Txt.writeContent(lines);
  downloadFile(volume.filename, blob);
};
</script>

<template>
  <div class="layout-content">
    <n-h1>OCR修复</n-h1>

    <bulletin>
      <n-p>
        OCR输出的文本通常存在额外的换行符，这会导致翻译器错误。你可以上传txt文件来修复这一问题。
      </n-p>
      <n-p>
        当前的修复方法是检测每一行的结尾是否是字符（汉字/日文假名/韩文字符/英文字母），如果是的话则删除行尾的换行符。
      </n-p>
    </bulletin>

    <n-flex vertical>
      <n-upload
        :show-file-list="false"
        accept=".txt"
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
        <div v-for="volume of loadedVolumes">
          <n-button type="success" text @click="fix(volume)">
            [点击下载修复版]
            {{ volume.filename }}
          </n-button>
        </div>
      </n-flex>
    </n-flex>
  </div>
</template>
