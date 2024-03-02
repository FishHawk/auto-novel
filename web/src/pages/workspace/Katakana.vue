<script lang="ts" setup>
import { UploadCustomRequestOptions, useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { Epub } from '@/data/epub/epub';
import { Txt } from '@/data/epub/txt';
import { useIsWideScreen } from '@/data/util';
import { PlusOutlined } from '@vicons/material';
import { PersonalVolumesManager, Translator } from '@/data/translator';

const message = useMessage();
const isWideScreen = useIsWideScreen(850);

interface FileInfo {
  source: 'tmp' | 'local';
  filename: string;
  content: string;
}
const fileInfo = ref<FileInfo>();
const showPreviewModal = ref(false);

const loadFile = async (
  source: 'tmp' | 'local',
  filename: string,
  file: File
) => {
  if (filename.endsWith('.txt')) {
    const content = await Txt.readContent(file);
    fileInfo.value = { source, filename, content };
  } else if (filename.endsWith('.epub')) {
    const fullContent: string[] = [];
    await Epub.forEachXHtmlFile(file, (_path, doc) => {
      Array.from(doc.getElementsByClassName('rt')).forEach((node) =>
        node.parentNode!!.removeChild(node)
      );
      fullContent.push(doc.body.textContent ?? '');
    });
    fileInfo.value = {
      source,
      filename: file.name,
      content: fullContent.join('\n'),
    };
  }
};

const loadLocalFile = (volumeId: string) =>
  PersonalVolumesManager.getFile(volumeId)
    .then((file) => loadFile('local', volumeId, file))
    .catch((error) => message.error(`术语表提交失败：${error}`));

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  if (!file.file) return;
  loadFile('tmp', file.name, file.file)
    .then(onFinish)
    .catch((err) => {
      message.error('文件读取失败:' + err);
      onError();
    });
};

const katakanaThredhold = ref(10);
const katakanaCounter = computed(() => {
  if (!fileInfo.value) return new Map();
  const regexp = /[\u30A0-\u30FF]{2,}/g;
  const matches = fileInfo.value.content.matchAll(regexp);
  const katakanaCounter = new Map<string, number>();
  for (const match of matches) {
    const w = match[0];
    katakanaCounter.set(w, (katakanaCounter.get(w) || 0) + 1);
  }

  const sortedKatakanaCounter = new Map(
    [...katakanaCounter].sort(([_w1, c1], [_w2, c2]) => c2 - c1)
  );

  return sortedKatakanaCounter;
});
const katakanas = computed(() => {
  return new Map(
    [...katakanaCounter.value].filter(([w, c]) => c > katakanaThredhold.value)
  );
});

const copyTranslationJson = async () => {
  const obj = Object.fromEntries(
    Array.from(katakanas.value).map(([key]) => [
      key,
      katakanaTranslations.value[key] ?? '',
    ])
  );
  const jsonString = JSON.stringify(obj, null, 2);
  navigator.clipboard.writeText(jsonString);
  message.info('已经将翻译结果复制到剪切板');
};

const katakanaTranslations = ref<{ [key: string]: string }>({});
const translateKatakanas = async (id: 'baidu' | 'youdao') => {
  const jpWords = [...katakanas.value.keys()];
  try {
    const translator = await Translator.create(
      {
        id,
        log: () => {},
      },
      false
    );
    const zhWords = await translator.translate(jpWords, {});
    console.log(jpWords);
    console.log(zhWords);

    const jpToZh: { [key: string]: string } = {};
    jpWords.forEach((jpWord, index) => {
      jpToZh[jpWord] = zhWords[index];
    });
    katakanaTranslations.value = jpToZh;
  } catch (e: any) {
    message.error(`翻译器错误：${e}`);
  }
};
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <n-h1>片假名统计</n-h1>

    <n-flex align="center">
      <div>
        <n-upload
          accept=".txt,.epub"
          :custom-request="customRequest"
          :show-file-list="false"
        >
          <c-button label="加载文件" :icon="PlusOutlined" />
        </n-upload>
      </div>
    </n-flex>

    <n-p v-if="fileInfo" style="margin-bottom: 0">
      {{ fileInfo.source === 'tmp' ? '临时文件' : '本地文件' }}
      /
      <c-button
        :label="`[点击预览] ${fileInfo.filename}`"
        text
        type="primary"
        @click="showPreviewModal = true"
      />
    </n-p>
    <n-p v-else depth="3" style="margin-bottom: 0">未选择文件</n-p>

    <section-header :title="`统计结果（${katakanas.size}个）`" />
    <n-flex vertical>
      <n-flex>
        <c-button label="复制术语表" @click="copyTranslationJson()" />
        <c-button label="百度翻译" async @click="translateKatakanas('baidu')" />
        <c-button
          label="有道翻译"
          async
          @click="translateKatakanas('youdao')"
        />
      </n-flex>

      <n-flex align="baseline">
        次数下限
        <n-input-number v-model:value="katakanaThredhold" clearable />
      </n-flex>
    </n-flex>

    <n-card v-if="katakanas.size > 0" embedded style="margin-top: 20px">
      <n-scrollbar trigger="none" style="max-height: 300px">
        <table style="border-spacing: 30px 0">
          <tr v-for="[word, number] in katakanas" :key="word">
            <td style="min-width: 100px">{{ word }}</td>
            <td>=></td>
            <td>{{ number }}</td>

            <template v-if="katakanaTranslations[word]">
              <td>=></td>
              <td>{{ katakanaTranslations[word] }}</td>
            </template>
          </tr>
        </table>
      </n-scrollbar>
    </n-card>

    <template #sidebar>
      <local-volume-list-katakana @volume-loaded="loadLocalFile" />
    </template>
  </c-layout>

  <c-modal title="预览（前100行）" v-model:show="showPreviewModal">
    <template v-if="fileInfo">
      <n-p v-for="line of fileInfo.content.split('\n').slice(0, 100)">
        {{ line }}
      </n-p>
    </template>
  </c-modal>
</template>
