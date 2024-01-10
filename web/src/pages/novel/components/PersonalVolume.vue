<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref, toRaw } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { PersonalVolume } from '@/data/api/api_user_personal';
import { useSettingStore } from '@/data/stores/setting';
import {
  buildPersonalTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { TranslatorId } from '@/data/translator/translator';

const props = defineProps<{
  volume: PersonalVolume;
  getParams: () => {
    translateExpireChapter: boolean;
  };
}>();

const emit = defineEmits<{ requireRefresh: [] }>();

const message = useMessage();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();
const setting = useSettingStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') => {
  const params = props.getParams();
  return translateTask?.value?.startTask(
    { type: 'personal', volumeId: props.volume.volumeId },
    {
      ...params,
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
    const { filename, blob } = await import('@/data/translator').then((it) =>
      it.PersonalVolumesManager.makeTranslationVolumeFile({
        volumeId: props.volume.volumeId,
        lang,
        translationsMode,
        translations,
      })
    );

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
  import('@/data/translator')
    .then((it) => it.PersonalVolumesManager.deleteVolume(volumeId))
    .then(() => message.info('删除成功'))
    .then(() => emit('requireRefresh'))
    .catch((error) => message.error(`删除失败：${error}`));

const submitGlossary = (
  volumeId: string,
  glossary: { [key: string]: string }
) =>
  import('@/data/translator')
    .then((it) =>
      it.PersonalVolumesManager.updateGlossary(volumeId, toRaw(glossary))
    )
    .then(() => message.success('术语表提交成功'))
    .catch((error) => message.error(`术语表提交失败：${error}`))
    .then(() => {});

const submitJob = (translatorId: 'sakura' | 'gpt') => {
  const { translateExpireChapter } = props.getParams();

  const task = buildPersonalTranslateTask(props.volume.volumeId, {
    start: 0,
    end: 65535,
    expire: translateExpireChapter,
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
    <n-space align="center" justify="space-between" :wrap="false">
      <n-space vertical>
        <n-text>{{ volume.volumeId }}</n-text>
        <n-space>
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
        </n-space>
      </n-space>
      <async-button @async-click="downloadFile">
        <template #icon>
          <n-icon :component="FileDownloadFilled" />
        </template>
        下载
      </async-button>
    </n-space>

    <n-collapse-transition :show="showGlossaryEditor" style="margin-top: 16px">
      <n-list bordered>
        <n-list-item>
          <AdvanceOption
            title="术语表"
            description="术语表过大可能会使得翻译质量下降（例如：百度/有道将无法从判断人名性别，导致人称代词错误）。"
          >
            <GlossaryEdit
              :glossary="volume.glossary"
              :submit="() => submitGlossary(volume.volumeId, volume.glossary)"
            />
          </AdvanceOption>
        </n-list-item>
      </n-list>
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
