<script lang="ts" setup>
import { DeleteOutlineOutlined, PlusOutlined } from '@vicons/material';
import { UploadCustomRequestOptions } from 'naive-ui';

import { Locator } from '@/data';
import { Translator, TranslatorConfig } from '@/domain/translate';
import { Glossary } from '@/model/Glossary';
import { notice } from '@/pages/components/NoticeBoard.vue';
import { useIsWideScreen } from '@/pages/util';
import { Epub, Txt } from '@/util/file';

import LoadedVolume from './components/LoadedVolume.vue';

const message = useMessage();
const isWideScreen = useIsWideScreen(850);
const sakuraWorkspace = Locator.sakuraWorkspaceRepository().ref;

interface LoadedVolume {
  source: 'tmp' | 'local';
  filename: string;
  content: string;
  katakanas: Map<string, number>;
}

const loadedVolumes = ref<LoadedVolume[]>([]);

const countKatakana = (content: string) => {
  const regexp = /[\u30A0-\u30FF]{2,}/g;
  const matches = content.matchAll(regexp);
  const katakanaCounter = new Map<string, number>();
  for (const match of matches) {
    const w = match[0];
    katakanaCounter.set(w, (katakanaCounter.get(w) || 0) + 1);
  }
  const sortedKatakanaCounter = new Map(
    [...katakanaCounter].sort(([_w1, c1], [_w2, c2]) => c2 - c1)
  );
  return sortedKatakanaCounter;
};

const loadVolume = async (
  source: 'tmp' | 'local',
  filename: string,
  file: File
) => {
  if (
    loadedVolumes.value.find(
      (it) => it.source === source && it.filename === filename
    ) !== undefined
  ) {
    message.warning('文件已经载入');
    return;
  }

  let content: string;
  if (filename.endsWith('.txt') || filename.endsWith('.srt')) {
    content = await Txt.readContent(file);
  } else if (filename.endsWith('.epub')) {
    const fullContent: string[] = [];
    await Epub.forEachXHtmlFile(file, (_path, doc) => {
      Array.from(doc.getElementsByClassName('rt')).forEach((node) =>
        node.parentNode!!.removeChild(node)
      );
      fullContent.push(doc.body.textContent ?? '');
    });
    content = fullContent.join('\n');
  } else {
    return;
  }
  loadedVolumes.value.push({
    source,
    filename,
    content,
    katakanas: countKatakana(content),
  });
};

const deleteVolume = (volume: LoadedVolume) => {
  loadedVolumes.value = loadedVolumes.value.filter(
    (it) => !(it.source === volume.source && it.filename === volume.filename)
  );
};

const loadLocalFile = (volumeId: string) =>
  Locator.localVolumeRepository()
    .then((repo) => repo.getFile(volumeId))
    .then((file) => {
      if (file === undefined) {
        throw '小说不存在';
      }
      return loadVolume('local', volumeId, file.file);
    })
    .catch((error) => message.error(`文件载入失败：${error}`));

const customRequest = ({
  file,
  onFinish,
  onError,
}: UploadCustomRequestOptions) => {
  if (!file.file) return;
  loadVolume('tmp', file.name, file.file)
    .then(onFinish)
    .catch((err) => {
      message.error('文件载入失败:' + err);
      onError();
    });
};

const katakanaThredhold = ref(10);

const katakanaMerged = computed(() => {
  const map = new Map<string, number>();
  loadedVolumes.value.forEach(({ katakanas }) => {
    katakanas.forEach((value, key) => {
      map.set(key, (map.get(key) ?? 0) + value);
    });
  });
  return map;
});

const katakanas = computed(() => {
  return new Map(
    [...katakanaMerged.value].filter(([w, c]) => c > katakanaThredhold.value)
  );
});

const copyTranslationJson = async () => {
  const obj = Object.fromEntries(
    Array.from(katakanas.value).map(([key]) => [
      key,
      katakanaTranslations.value[key] ?? '',
    ])
  );
  const jsonString = Glossary.encodeToText(obj);
  navigator.clipboard.writeText(jsonString);
  message.info('已经将翻译结果复制到剪切板');
};

const showSakuraSelectModal = ref(false);
const selectedSakuraWorkerId = ref(sakuraWorkspace.value.workers[0]?.id);

const katakanaTranslations = ref<{ [key: string]: string }>({});
const translateKatakanas = async (id: 'baidu' | 'youdao' | 'sakura') => {
  const jpWords = [...katakanas.value.keys()];
  let config: TranslatorConfig;
  if (id === 'sakura') {
    const worker = sakuraWorkspace.value.workers.find(
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

const showListModal = ref(false);

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

    <c-action-wrapper title="载入文件">
      <n-flex vertical style="margin: 20px 0">
        <n-flex style="margin-bottom: 8px">
          <c-button
            label="加载本地小说"
            :icon="PlusOutlined"
            size="small"
            @click="showListModal = true"
          />
          <div>
            <n-upload
              accept=".txt,.epub"
              :custom-request="customRequest"
              :show-file-list="false"
            >
              <c-button label="加载文件" :icon="PlusOutlined" size="small" />
            </n-upload>
          </div>
          <c-button
            label="全部移除"
            :icon="DeleteOutlineOutlined"
            size="small"
            @click="loadedVolumes = []"
          />
        </n-flex>
        <n-text v-for="volume of loadedVolumes">
          <loaded-volume :volume="volume" @delete="deleteVolume(volume)" />
        </n-text>

        <n-text v-if="loadedVolumes.length === 0" depth="3">
          未载入文件
        </n-text>
      </n-flex>
    </c-action-wrapper>

    <n-flex vertical>
      <c-action-wrapper title="次数下限">
        <n-input-number
          v-model:value="katakanaThredhold"
          clearable
          size="small"
          style="width: 16em"
        />
      </c-action-wrapper>
      <c-action-wrapper title="操作">
        <n-flex vertical>
          <n-button-group size="small">
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

          <n-button-group size="small">
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

    <n-divider />

    <div v-if="katakanas.size !== 0">
      <n-scrollbar
        trigger="none"
        style="max-height: 60vh; max-width: 400px; margin-top: 30px"
      >
        <n-table striped size="small" style="font-size: 12px">
          <tr v-for="[word, number] in katakanas" :key="word">
            <td style="min-width: 100px">{{ word }}</td>
            <td nowrap="nowrap">=></td>
            <td>
              {{ number }}
              <n-text
                v-if="katakanaTranslations[word]"
                style="margin-left: 16px"
              >
                {{ katakanaTranslations[word] }}
              </n-text>
            </td>
          </tr>
        </n-table>
      </n-scrollbar>
    </div>

    <template #sidebar>
      <local-volume-list-katakana @volume-loaded="loadLocalFile" />
    </template>

    <c-drawer-right
      v-if="!isWideScreen"
      v-model:show="showListModal"
      title="本地小说"
    >
      <div style="padding: 24px 16px">
        <local-volume-list-katakana hide-title @volume-loaded="loadLocalFile" />
      </div>
    </c-drawer-right>
  </c-layout>

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
