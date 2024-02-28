<script lang="ts" setup>
import { FileDownloadOutlined } from '@vicons/material';
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
        label: `GPT(${props.volume.gpt}/${props.volume.total})`,
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

  try {
    const { filename, blob } =
      await PersonalVolumesManager.makeTranslationVolumeFile({
        volumeId: props.volume.volumeId,
        lang: mode,
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
    .catch((error) => message.error(`术语表提交失败：${error}`));

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
      <n-flex :size="4" vertical>
        <n-text>{{ volume.volumeId }}</n-text>

        <n-text depth="3">
          总计 {{ volume.total }} / 百度 {{ volume.baidu }} / 有道
          {{ volume.youdao }} / GPT {{ volume.gpt }} / Sakura
          {{ volume.sakura }}
        </n-text>

        <n-flex :size="8">
          <c-button
            v-if="setting.enabledTranslator.includes('baidu')"
            label="更新百度"
            size="tiny"
            secondary
            @click="startTranslateTask('baidu')"
          />
          <c-button
            v-if="setting.enabledTranslator.includes('youdao')"
            label="更新有道"
            size="tiny"
            secondary
            @click="startTranslateTask('youdao')"
          />

          <c-button
            v-if="setting.enabledTranslator.includes('gpt')"
            label="排队GPT"
            size="tiny"
            secondary
            @click="submitJob('gpt')"
          />
          <c-button
            v-if="setting.enabledTranslator.includes('sakura')"
            label="排队Sakura"
            size="tiny"
            secondary
            @click="submitJob('sakura')"
          />

          <c-button
            v-if="volume.volumeId.endsWith('.txt')"
            label="阅读"
            tag="a"
            :href="`/workspace/reader/${encodeURIComponent(volume.volumeId)}/0`"
            size="tiny"
            secondary
          />

          <c-button
            :label="`术语表[${Object.keys(volume.glossary).length}]`"
            size="tiny"
            secondary
            @click="showGlossaryEditor = !showGlossaryEditor"
          />

          <n-popconfirm
            :show-icon="false"
            @positive-click="deleteVolume(volume.volumeId)"
            :negative-text="null"
          >
            <template #trigger>
              <c-button label="删除" type="error" size="tiny" secondary />
            </template>
            真的要删除{{ volume.volumeId }}吗？
          </n-popconfirm>
        </n-flex>
      </n-flex>
      <c-button
        label="下载"
        :icon="FileDownloadOutlined"
        async
        @click="downloadFile"
      />
    </n-flex>

    <n-collapse-transition :show="showGlossaryEditor" style="margin-top: 16px">
      <glossary-edit :glossary="volume.glossary" />
      <c-button
        label="提交"
        async
        type="primary"
        secondary
        size="small"
        @click="submitGlossary(volume.volumeId, volume.glossary)"
      />
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
