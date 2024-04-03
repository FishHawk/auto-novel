<script lang="ts" setup>
import { FileDownloadOutlined } from '@vicons/material';

import { WenkuNovelRepository } from '@/data/api';
import { SettingRepository } from '@/data/stores';
import { useUserDataStore } from '@/data/stores/user_data';
import {
  buildWenkuTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { VolumeJpDto } from '@/model/WenkuNovel';
import TranslateTask from '@/pages/components/TranslateTask.vue';

const { novelId, volume, getParams } = defineProps<{
  novelId: string;
  volume: VolumeJpDto;
  getParams: () => {
    translateExpireChapter: boolean;
    autoTop: boolean;
  };
}>();

const emit = defineEmits<{ delete: [] }>();

const message = useMessage();
const userData = useUserDataStore();
const setting = SettingRepository.ref();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') => {
  const params = getParams();
  return translateTask?.value?.startTask(
    { type: 'wenku', novelId, volumeId: volume.volumeId },
    {
      ...params,
      overriteToc: false,
      syncFromProvider: false,
      startIndex: 0,
      endIndex: 65536,
    },
    { id: translatorId }
  );
};

const file = computed(() => {
  const { mode, translationsMode, translations } = setting.value.downloadFormat;

  const { url, filename } = WenkuNovelRepository.createFileUrl({
    novelId,
    volumeId: volume.volumeId,
    mode,
    translationsMode,
    translations,
  });
  return { url, filename };
});

const submitJob = (id: 'gpt' | 'sakura') => {
  const { translateExpireChapter, autoTop } = getParams();
  const task = buildWenkuTranslateTask(novelId, volume.volumeId, {
    start: 0,
    end: 65535,
    expire: translateExpireChapter,
  });
  const workspace = id === 'gpt' ? gptWorkspace : sakuraWorkspace;
  const job = {
    task,
    description: volume.volumeId,
    createAt: Date.now(),
  };
  const success = workspace.addJob(job);
  if (autoTop) workspace.topJob(job);
  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};
</script>

<template>
  <n-flex align="center" justify="space-between" :wrap="false">
    <n-flex :size="4" vertical>
      <n-text>{{ volume.volumeId }}</n-text>

      <n-text depth="3">
        总计 {{ volume.total }} / 百度 {{ volume.baidu }} / 有道
        {{ volume.youdao }} / GPT {{ volume.gpt }} / Sakura {{ volume.sakura }}
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
          @action="submitJob('gpt')"
        />
        <c-button
          v-if="setting.enabledTranslator.includes('sakura')"
          label="排队Sakura"
          size="tiny"
          secondary
          @action="submitJob('sakura')"
        />

        <n-popconfirm
          v-if="userData.isMaintainer"
          :show-icon="false"
          @positive-click="emit('delete')"
          :negative-text="null"
          style="max-width: 300px"
        >
          <template #trigger>
            <c-button label="删除" type="error" size="tiny" secondary />
          </template>
          真的要删除吗？
          <br />
          {{ volume.volumeId }}
        </n-popconfirm>
      </n-flex>
    </n-flex>

    <c-button
      label="下载"
      :icon="FileDownloadOutlined"
      tag="a"
      :href="file.url"
      :download="file.filename"
      target="_blank"
    />
  </n-flex>

  <TranslateTask
    ref="translateTask"
    @update:baidu="(zh) => (volume.baidu = zh)"
    @update:youdao="(zh) => (volume.youdao = zh)"
    @update:gpt="(zh) => (volume.gpt = zh)"
    @update:sakura="(zh) => (volume.sakura = zh)"
    style="margin-top: 20px"
  />
</template>
