<script lang="ts" setup>
import { UploadCustomRequestOptions, useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import { notice } from '@/components/NoticeBoard.vue';
import { Epub } from '@/data/epub/epub';
import { Txt } from '@/data/epub/txt';
import { useSakuraWorkspaceStore } from '@/data/stores/workspace';
import { PersonalVolumesManager, Translator } from '@/data/translator';
import { TranslatorConfig } from '@/data/translator/translator';
import { useIsWideScreen } from '@/data/util';
import { PlusOutlined } from '@vicons/material';

const message = useMessage();
const isWideScreen = useIsWideScreen(850);
const sakuraWorkspace = useSakuraWorkspaceStore();

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

const showSakuraSelectModal = ref(false);
const selectedSakuraWorkerId = ref(sakuraWorkspace.workers.at(0)?.id);

const katakanaTranslations = ref<{ [key: string]: string }>({});
const translateKatakanas = async (id: 'baidu' | 'youdao' | 'sakura') => {
  const jpWords = [...katakanas.value.keys()];
  let config: TranslatorConfig;
  if (id === 'sakura') {
    const worker = sakuraWorkspace.workers.find(
      (it) => it.id === selectedSakuraWorkerId.value
    );
    if (worker === undefined) {
      message.error('未选择Sakura翻译器');
      return;
    }
    config = {
      id,
      log: () => {},
      endpoint: worker.endpoint,
      useLlamaApi: worker.useLlamaApi ?? false,
    };
  } else {
    config = {
      id,
      log: () => {},
    };
  }
  try {
    const translator = await Translator.create(config, false);
    const zhWords = await translator.translate(jpWords, {});

    const jpToZh: { [key: string]: string } = {};
    jpWords.forEach((jpWord, index) => {
      jpToZh[jpWord] = zhWords[index];
    });
    katakanaTranslations.value = jpToZh;
  } catch (e: any) {
    message.error(`翻译器错误：${e}`);
  }
};

const notices = [
  notice('术语表辅助制作工具正在开发中，当前方案分为识别和翻译两步。'),
  notice('识别阶段：根据片假名词汇出现频率判断可能是术语的词汇。'),
  notice('翻译阶段：直接翻译日语词汇。'),
  notice('注意，这是辅助制作，不是全自动生成，使用前务必检查结果。', true),
];
</script>

<template>
  <c-layout :sidebar="isWideScreen" :sidebar-width="320" class="layout-content">
    <n-h1>术语表工作区</n-h1>

    <notice-board :notices="notices" />

    <n-p>
      <n-upload
        accept=".txt,.epub"
        :custom-request="customRequest"
        :show-file-list="false"
      >
        <c-button label="加载文件" :icon="PlusOutlined" />
      </n-upload>
    </n-p>

    <n-p v-if="fileInfo" style="margin-bottom: 0">
      {{ fileInfo.source === 'tmp' ? '临时文件' : '本地文件' }}
      /
      {{ fileInfo.filename }}
      <c-button
        :label="`[预览] `"
        text
        type="primary"
        @action="showPreviewModal = true"
      />
    </n-p>
    <n-p v-else depth="3" style="margin-bottom: 0">未选择文件</n-p>

    <section-header :title="`统计结果（${katakanas.size}个）`" />
    <n-flex vertical>
      <c-action-wrapper title="次数下限">
        <n-input-number v-model:value="katakanaThredhold" clearable />
      </c-action-wrapper>
      <c-action-wrapper title="操作">
        <n-flex vertical>
          <n-button-group>
            <c-button
              label="复制术语表"
              :round="false"
              @action="copyTranslationJson()"
            />
            <c-button
              label="百度翻译"
              :round="false"
              @action="translateKatakanas('baidu')"
            />
            <c-button
              label="有道翻译"
              :round="false"
              @action="translateKatakanas('youdao')"
            />
          </n-button-group>

          <n-button-group>
            <c-button
              :label="`Sakura翻译-${selectedSakuraWorkerId ?? '未选中'}`"
              :round="false"
              @action="translateKatakanas('sakura')"
            />
            <c-button
              label="选择翻译器"
              :round="false"
              @action="showSakuraSelectModal = true"
            />
          </n-button-group>
        </n-flex>
      </c-action-wrapper>
    </n-flex>

    <n-card v-if="katakanas.size > 0" embedded style="margin-top: 20px">
      <n-scrollbar trigger="none" style="max-height: 300px">
        <table id="glossary" style="border-spacing: 10px 0">
          <tr v-for="[word, number] in katakanas" :key="word">
            <td style="min-width: 100px">{{ word }}</td>
            <td>=></td>
            <td>{{ number }}</td>
            <template v-if="katakanaTranslations[word]">
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

  <c-modal title="选择Sakura翻译器" v-model:show="showSakuraSelectModal">
    <n-radio-group v-model:value="selectedSakuraWorkerId">
      <n-flex vertical>
        <n-radio
          v-for="worker of sakuraWorkspace.workers"
          :key="worker.id"
          :value="worker.id"
        >
          {{ worker.id }}
          <n-text depth="3">
            {{ worker.endpoint }}
          </n-text>
        </n-radio>
      </n-flex>
    </n-radio-group>
  </c-modal>
</template>

<style scoped>
.id td {
  white-space: nowrap;
}
</style>
