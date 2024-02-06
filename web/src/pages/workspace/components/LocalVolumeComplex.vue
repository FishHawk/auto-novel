<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref, toRaw } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { useSettingStore } from '@/data/stores/setting';
import {
  buildPersonalTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { PersonalVolumesManager, TranslatorId } from '@/data/translator';

export interface Volume {
  volumeId: string;
  createAt: number;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  glossary: { [key: string]: string };
}

const props = defineProps<{ volume: Volume }>();

const emit = defineEmits<{ requireRefresh: [] }>();

const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();
const setting = useSettingStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') => {
  return translateTask?.value?.startTask(
    { type: 'personal', volumeId: props.volume.volumeId },
    {
      translateExpireChapter: true,
      syncFromProvider: false,
      startIndex: 0,
      endIndex: 65535,
    },
    { id: translatorId }
  );
};

const translatorLabels = computed(
  () =>
    <{ label: string; translatorId: TranslatorId }[]>[
      {
        label: `百度(${props.volume.baidu}/${props.volume.total})`,
        translatorId: 'baidu',
      },
      {
        label: `有道(${props.volume.youdao}/${props.volume.total})`,
        translatorId: 'youdao',
      },
      {
        label: `GPT3(${props.volume.gpt}/${props.volume.total})`,
        translatorId: 'gpt',
      },
      {
        label: `Sakura(${props.volume.sakura}/${props.volume.total})`,
        translatorId: 'sakura',
      },
    ]
);

const downloadFile = async () => {
  const { mode, translationsMode, translations } = setting.downloadFormat;

  let lang: 'zh' | 'zh-jp' | 'jp-zh';
  if (mode === 'jp' || mode === 'zh') {
    lang = 'zh';
  } else if (mode === 'mix') {
    lang = 'zh-jp';
  } else {
    lang = 'jp-zh';
  }

  try {
    const { filename, blob } =
      await PersonalVolumesManager.makeTranslationVolumeFile({
        volumeId: props.volume.volumeId,
        lang,
        translationsMode,
        translations,
      });

    const el = document.createElement('a');
    el.href = URL.createObjectURL(blob);
    el.target = '_blank';
    el.download = filename;
    el.click();
  } catch (error) {
    message.error(`文件生成错误：${error}`);
  }
};

const deleteVolume = (volumeId: string) =>
  PersonalVolumesManager.deleteVolume(volumeId)
    .then(() => message.info('删除成功'))
    .then(() => emit('requireRefresh'))
    .catch((error) => message.error(`删除失败：${error}`));

const submitGlossary = (
  volumeId: string,
  glossary: { [key: string]: string }
) =>
  PersonalVolumesManager.updateGlossary(volumeId, toRaw(glossary))
    .then(() => message.success('术语表提交成功'))
    .catch((error) => message.error(`术语表提交失败：${error}`))
    .then(() => {});

const submitJob = (translatorId: 'sakura' | 'gpt') => {
  const task = buildPersonalTranslateTask(props.volume.volumeId, {
    start: 0,
    end: 65535,
    expire: true,
  });

  let success = false;
  if (translatorId === 'gpt') {
    success = gptWorkspace.addJob({
      task,
      description: props.volume.volumeId,
      createAt: Date.now(),
    });
  } else {
    success = sakuraWorkspace.addJob({
      task,
      description: props.volume.volumeId,
      createAt: Date.now(),
    });
  }
  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};

const showGlossaryEditor = ref(false);
</script>

<template>
  <div>
    <n-flex align="center" justify="space-between" :wrap="false">
      <n-flex vertical>
        <n-text>{{ volume.volumeId }}</n-text>
        <n-flex>
          <template v-for="{ translatorId, label } of translatorLabels">
            <n-button
              v-if="translatorId !== 'sakura' && translatorId !== 'gpt'"
              text
              type="primary"
              @click="startTranslateTask(translatorId)"
            >
              更新{{ label }}
            </n-button>

            <n-button
              v-else
              text
              type="primary"
              @click="() => submitJob(translatorId)"
            >
              排队{{ label }}
            </n-button>
          </template>

          <n-button
            text
            type="primary"
            @click="showGlossaryEditor = !showGlossaryEditor"
          >
            编辑术语表
          </n-button>

          <n-popconfirm
            :show-icon="false"
            @positive-click="deleteVolume(volume.volumeId)"
            :negative-text="null"
          >
            <template #trigger>
              <n-button text type="error"> 删除 </n-button>
            </template>
            真的要删除{{ volume.volumeId }}吗？
          </n-popconfirm>
        </n-flex>
      </n-flex>
      <c-button
        label="下载"
        :icon="FileDownloadFilled"
        async
        @click="downloadFile"
      />
    </n-flex>

    <n-collapse-transition :show="showGlossaryEditor" style="margin-top: 16px">
      <glossary-edit :glossary="volume.glossary" />
      <n-button
        type="primary"
        secondary
        size="small"
        @click="() => submitGlossary(volume.volumeId, volume.glossary)"
      >
        提交
      </n-button>
    </n-collapse-transition>

    <TranslateTask
      ref="translateTask"
      @update:baidu="(zh) => (volume.baidu = zh)"
      @update:youdao="(zh) => (volume.youdao = zh)"
      @update:gpt="(zh) => (volume.gpt = zh)"
      @update:sakura="(zh) => (volume.sakura = zh)"
      style="margin-top: 20px"
    />
  </div>
</template>
