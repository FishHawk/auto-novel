<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { ApiSakura } from '@/data/api/api_sakura';
import { ApiWebNovel } from '@/data/api/api_web_novel';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import {
  buildWebTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { TranslatorId } from '@/data/translator/translator';

import AdvanceOptions from './AdvanceOptions.vue';

const props = defineProps<{
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  total: number;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  glossary: { [key: string]: string };
}>();

const { providerId, novelId, titleJp, titleZh, total } = props;

const emit = defineEmits<{
  'update:jp': [number];
  'update:baidu': [number];
  'update:youdao': [number];
  'update:gpt': [number];
}>();

const setting = useSettingStore();
const readerSetting = useReaderSettingStore();
const message = useMessage();
const userData = useUserDataStore();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const advanceOptions = ref<InstanceType<typeof AdvanceOptions>>();
const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') =>
  translateTask?.value?.startTask(
    { type: 'web', providerId, novelId },
    advanceOptions.value!!.getTranslationOptions(),
    { id: translatorId }
  );

const files = computed(() => {
  const title =
    setting.downloadFilenameType === 'jp' ? titleJp : titleZh ?? titleJp;

  const { mode, translationsMode, translations } =
    setting.isDownloadFormatSameAsReaderFormat
      ? readerSetting
      : setting.downloadFormat;

  let lang: 'zh' | 'zh-jp' | 'jp-zh';
  if (mode === 'jp' || mode === 'zh') {
    lang = 'zh';
  } else if (mode === 'mix') {
    lang = 'zh-jp';
  } else {
    lang = 'jp-zh';
  }

  const createFileUrl = (
    label: string,
    type: 'epub' | 'txt',
    isJp: boolean
  ) => ({
    label,
    ...ApiWebNovel.createFileUrl({
      providerId,
      novelId,
      lang: isJp ? 'jp' : lang,
      translationsMode,
      translations,
      type,
      title,
    }),
  });

  return [
    createFileUrl('EPUB', 'epub', false),
    createFileUrl('TXT', 'txt', false),
    createFileUrl('日文EPUB', 'epub', true),
    createFileUrl('日文TXT', 'txt', true),
  ];
});

const submitGlossary = async () => {
  const result = await ApiWebNovel.updateGlossary(
    props.providerId,
    props.novelId,
    props.glossary
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
};

const submitPublicSakuraJob = async () => {
  const options = advanceOptions.value!!.getTranslationOptions();
  const result = await ApiSakura.createSakuraJobWebTranslate(
    providerId,
    novelId,
    {
      start: options.startIndex,
      end: options.endIndex,
      expire: options.translateExpireChapter,
    }
  );
  if (result.ok) {
    message.info('排队成功');
  } else {
    message.error('排队失败:' + result.error.message);
  }
};

const submitJob = (id: 'gpt' | 'sakura') => {
  if (id === 'sakura' && !userData.isTrusted) {
    message.error('目前普通用户无法使用Sakura工作区翻译网络小说');
    return;
  }

  const options = advanceOptions.value!!.getTranslationOptions();
  const task = buildWebTranslateTask(providerId, novelId, {
    start: options.startIndex,
    end: options.endIndex,
    expire: options.translateExpireChapter,
  });
  const workspace = id === 'gpt' ? gptWorkspace : sakuraWorkspace;
  const success = workspace.addJob({
    task,
    description: titleJp,
    createAt: Date.now(),
  });
  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};

const translatorLabels = computed(
  () =>
    <{ label: string; translatorId: TranslatorId }[]>[
      {
        label: `百度(${props.baidu}/${total})`,
        translatorId: 'baidu',
      },
      {
        label: `有道(${props.youdao}/${total})`,
        translatorId: 'youdao',
      },
      {
        label: `GPT3(${props.gpt}/${total})`,
        translatorId: 'gpt',
      },
      {
        label: `Sakura(${props.sakura}/${total})`,
        translatorId: 'sakura',
      },
    ]
);
</script>

<template>
  <advance-options
    ref="advanceOptions"
    type="web"
    :glossary="glossary"
    :submit="submitGlossary"
  />

  <n-p>
    <n-space>
      <n-button
        v-for="file of files"
        tag="a"
        :href="file.url"
        :download="file.filename"
        target="_blank"
      >
        <template #icon>
          <n-icon :component="FileDownloadFilled" />
        </template>
        下载{{ file.label }}
      </n-button>
    </n-space>
  </n-p>

  <n-p>
    <n-space>
      <template v-for="{ translatorId, label } of translatorLabels">
        <n-button
          v-if="translatorId === 'gpt' || translatorId === 'sakura'"
          @click="() => submitJob(translatorId)"
        >
          排队{{ label }}
        </n-button>
        <n-button v-else @click="startTranslateTask(translatorId)">
          更新{{ label }}
        </n-button>
      </template>

      <async-button @async-click="submitPublicSakuraJob">
        公用排队Sakura
      </async-button>
    </n-space>
  </n-p>

  <TranslateTask
    ref="translateTask"
    @update:jp="(zh) => emit('update:jp', zh)"
    @update:baidu="(zh) => emit('update:baidu', zh)"
    @update:youdao="(zh) => emit('update:youdao', zh)"
    @update:gpt="(zh) => emit('update:gpt', zh)"
    style="margin-top: 20px"
  />
</template>
