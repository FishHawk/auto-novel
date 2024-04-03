<script lang="ts" setup>
import { LocalVolumeRepository } from '@/data/local';
import { SettingRepository } from '@/data/stores';
import {
  buildPersonalTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { GenericNovelId } from '@/model/Common';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { TranslatorId } from '@/model/Translator';
import TranslateTask from '@/pages/components/TranslateTask.vue';

const props = defineProps<{ volume: LocalVolumeMetadata }>();

const message = useMessage();
const setting = SettingRepository.ref();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const calculateFinished = (translatorId: TranslatorId) =>
  props.volume.toc.filter((it) => it[translatorId]).length;

const baidu = ref(calculateFinished('baidu'));
const youdao = ref(calculateFinished('youdao'));
const gpt = ref(calculateFinished('gpt'));
const sakura = ref(calculateFinished('sakura'));

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') =>
  translateTask?.value?.startTask(
    { type: 'personal', volumeId: props.volume.id },
    {
      translateExpireChapter: true,
      overriteToc: false,
      syncFromProvider: false,
      startIndex: 0,
      endIndex: 65535,
    },
    { id: translatorId }
  );

const queueVolume = (translatorId: 'gpt' | 'sakura') => {
  const task = buildPersonalTranslateTask(props.volume.id, {
    start: 0,
    end: 65535,
    expire: true,
  });

  const workspace = translatorId === 'gpt' ? gptWorkspace : sakuraWorkspace;

  const success = workspace.addJob({
    task,
    description: props.volume.id,
    createAt: Date.now(),
  });

  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};

const downloadVolume = async () => {
  const { mode, translationsMode, translations } = setting.value.downloadFormat;

  try {
    const { filename, blob } = await LocalVolumeRepository.getTranslationFile({
      id: props.volume.id,
      mode,
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
</script>
<template>
  <n-flex :size="4" vertical>
    <n-text>{{ volume.id }}</n-text>

    <n-text depth="3">
      总计 {{ volume.toc.length }} / 百度 {{ baidu }} / 有道 {{ youdao }} / GPT
      {{ gpt }} / Sakura {{ sakura }}
    </n-text>

    <n-flex :size="8">
      <c-button
        v-if="setting.enabledTranslator.includes('baidu')"
        label="更新百度"
        size="tiny"
        secondary
        @action="startTranslateTask('baidu')"
      />
      <c-button
        v-if="setting.enabledTranslator.includes('youdao')"
        label="更新有道"
        size="tiny"
        secondary
        @action="startTranslateTask('youdao')"
      />

      <c-button
        v-if="setting.enabledTranslator.includes('gpt')"
        label="排队GPT"
        size="tiny"
        secondary
        @action="queueVolume('gpt')"
      />
      <c-button
        v-if="setting.enabledTranslator.includes('sakura')"
        label="排队Sakura"
        size="tiny"
        secondary
        @action="queueVolume('sakura')"
      />

      <router-link
        v-if="!volume.id.endsWith('.epub')"
        :to="`/workspace/reader/${encodeURIComponent(volume.id)}/0`"
      >
        <c-button label="阅读" size="tiny" secondary />
      </router-link>

      <glossary-button
        :gnid="GenericNovelId.local(volume.id)"
        :value="volume.glossary"
        size="tiny"
        secondary
      />

      <c-button label="下载" size="tiny" secondary @action="downloadVolume" />
      <slot />
    </n-flex>
  </n-flex>
  <TranslateTask
    ref="translateTask"
    style="margin-top: 20px"
    @update:baidu="(zh) => (baidu = zh)"
    @update:youdao="(zh) => (youdao = zh)"
  />
</template>
