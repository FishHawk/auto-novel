<script lang="ts" setup>
import { DeleteOutlined, PlusOutlined } from '@vicons/material';
import {
  UploadCustomRequestOptions,
  UploadFileInfo,
  useMessage,
} from 'naive-ui';
import { ref } from 'vue';

import { useSettingStore } from '@/data/stores/setting';
import { PersonalVolumesManager, TranslatorId } from '@/data/translator';

import { Volume } from './components/LocalVolumeComplex.vue';

const message = useMessage();
const setting = useSettingStore();

const volumes = ref<Volume[]>([]);

const loadVolumes = () => {
  PersonalVolumesManager.listVolumes().then((rawVolumes) => {
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
  PersonalVolumesManager.saveVolume(file.file!!)
    .then(onFinish)
    .catch((error) => {
      message.error(`上传失败:${error}`);
      onError();
    });
};

const showClearModal = ref(false);
const deleteAllVolumes = () => {
  return PersonalVolumesManager.deleteVolumesDb()
    .then(loadVolumes)
    .then(() => (showClearModal.value = false))
    .catch((error) => {
      message.error(`清空失败:${error}`);
    });
};

const modeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
  { value: 'mix', label: '中日' },
  { value: 'mix-reverse', label: '日中' },
];
const translationModeOptions = [
  { label: '优先', value: 'priority' },
  { label: '并列', value: 'parallel' },
];
const translationOptions: { label: string; value: TranslatorId }[] = [
  { label: 'Sakura', value: 'sakura' },
  { label: 'GPT3', value: 'gpt' },
  { label: '有道', value: 'youdao' },
  { label: '百度', value: 'baidu' },
];

const toggleTranslator = (id: TranslatorId) => {
  if (setting.downloadFormat.translations.includes(id)) {
    setting.downloadFormat.translations =
      setting.downloadFormat.translations.filter((it) => it !== id);
  } else {
    setting.downloadFormat.translations.push(id);
  }
};

const calculateTranslatorOrderLabel = (id: TranslatorId) => {
  const index = setting.downloadFormat.translations.indexOf(id);
  if (index < 0) {
    return '[x]';
  } else {
    return `[${index + 1}]`;
  }
};

const subPages = [
  {
    name: '片假名统计',
    description: '统计片假名次数来制作术语表。',
    href: '/workspace/katakana',
  },
  {
    name: 'GPT工作区',
    description: '使用GPT翻译小说。',
    href: '/workspace/gpt',
  },
  {
    name: 'Sakura工作区',
    description: '使用Sakura翻译小说。',
    href: '/workspace/sakura',
  },
  {
    name: '公用Sakura工作区[停用中]',
    description: '使用网友贡献的Sakura翻译器翻译小说。',
    href: '/workspace/sakura-public',
  },
];
</script>

<template>
  <div class="layout-content">
    <n-h1>工作区</n-h1>

    <n-ul>
      <n-li v-for="page of subPages">
        <RouterNA :to="page.href">
          <b>{{ page.name }}</b>
        </RouterNA>
        {{ page.description }}
      </n-li>
    </n-ul>

    <section-header title="本地文件" />
    <n-flex>
      <n-flex :wrap="false">
        <n-upload
          multiple
          directory-dnd
          :custom-request="customRequest"
          @finish="loadVolumes"
          @before-upload="beforeUpload"
        >
          <c-button label="添加文件" :icon="PlusOutlined" />
        </n-upload>

        <c-button
          label="清空文件"
          :icon="DeleteOutlined"
          @click="showClearModal = true"
        />
      </n-flex>
    </n-flex>

    <n-card embedded :bordered="false" size="small">
      <n-flex vertical>
        <n-flex align="baseline" :wrap="false">
          <n-text style="white-space: nowrap">语言</n-text>
          <n-radio-group
            v-model:value="setting.downloadFormat.mode"
            size="small"
          >
            <n-radio-button
              v-for="option in modeOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </n-radio-group>
        </n-flex>

        <n-flex align="baseline" :wrap="false">
          <n-text style="white-space: nowrap">翻译</n-text>
          <n-flex>
            <n-radio-group
              v-model:value="setting.downloadFormat.translationsMode"
              size="small"
            >
              <n-radio-button
                v-for="option in translationModeOptions"
                :key="option.value"
                :value="option.value"
                :label="option.label"
              />
            </n-radio-group>
            <n-button-group size="small">
              <n-button
                v-for="option in translationOptions"
                :focusable="false"
                ghost
                :type="
                  setting.downloadFormat.translations.includes(option.value)
                    ? 'primary'
                    : 'default'
                "
                :value="option.value"
                @click="toggleTranslator(option.value)"
              >
                {{ option.label }}
                {{ calculateTranslatorOrderLabel(option.value) }}
              </n-button>
            </n-button-group>
          </n-flex>
        </n-flex>
      </n-flex>
    </n-card>

    <n-divider />

    <n-list>
      <n-list-item v-for="volume of volumes">
        <local-volume-complex :volume="volume" @require-refresh="loadVolumes" />
      </n-list-item>
    </n-list>
  </div>

  <c-modal title="清空所有文件" v-model:show="showClearModal">
    <n-p>
      这将清空你的浏览器里面保存的所有EPUB/TXT文件，包括已经翻译的章节和术语表，无法恢复。
      你确定吗？
    </n-p>

    <template #action>
      <c-button label="确定" async type="primary" @click="deleteAllVolumes" />
    </template>
  </c-modal>
</template>
