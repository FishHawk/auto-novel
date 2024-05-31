<script lang="ts" setup>
import { useKeyModifier } from '@vueuse/core';

import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { LocalVolumeMetadata } from '@/model/LocalVolume';
import { TranslatorId, TranslateTaskDescriptor } from '@/model/Translator';

import TranslateTask from '@/pages/components/TranslateTask.vue';

const props = defineProps<{
  volume: LocalVolumeMetadata;
}>();

const message = useMessage();

const { setting } = Locator.settingRepository();

const calculateFinished = (translatorId: TranslatorId) =>
  props.volume.toc.filter((it) => it[translatorId]).length;

const baidu = ref(calculateFinished('baidu'));
const youdao = ref(calculateFinished('youdao'));
const gpt = ref(calculateFinished('gpt'));
const sakura = ref(calculateFinished('sakura'));

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') =>
  translateTask?.value?.startTask(
    { type: 'local', volumeId: props.volume.id },
    {
      level: 'expire',
      sync: false,
      forceMetadata: false,
      startIndex: 0,
      endIndex: 65535,
    },
    { id: translatorId },
  );

const shouldTopJob = useKeyModifier('Control');
const queueVolume = (translatorId: 'gpt' | 'sakura') => {
  const task = TranslateTaskDescriptor.local(props.volume.id, {
    level: 'expire',
    sync: false,
    forceMetadata: false,
    startIndex: 0,
    endIndex: 65535,
  });

  const workspace =
    translatorId === 'gpt'
      ? Locator.gptWorkspaceRepository()
      : Locator.sakuraWorkspaceRepository();

  const job = {
    task,
    description: props.volume.id,
    createAt: Date.now(),
  };

  const success = workspace.addJob(job);

  if (success) {
    message.success('排队成功');
    if (shouldTopJob.value) {
      workspace.topJob(job);
    }
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};
</script>

<template>
  <n-flex :size="4" vertical>
    <c-a
      v-if="!volume.id.endsWith('.epub')"
      :to="`/workspace/reader/${encodeURIComponent(volume.id)}/0`"
    >
      {{ volume.id }}
    </c-a>
    <n-text v-else>{{ volume.id }}</n-text>

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
      <glossary-button
        :gnid="GenericNovelId.local(volume.id)"
        :value="volume.glossary"
        size="tiny"
        secondary
      />
    </n-flex>
  </n-flex>
  <translate-task
    ref="translateTask"
    style="margin-top: 20px"
    @update:baidu="(zh) => (baidu = zh)"
    @update:youdao="(zh) => (youdao = zh)"
  />
</template>
