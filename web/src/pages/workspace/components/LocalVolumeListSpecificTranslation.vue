<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { ref, toRaw } from 'vue';

import { downloadModeOptions, useSettingStore } from '@/data/stores/setting';
import {
  buildPersonalTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { PersonalVolumesManager } from '@/data/translator';
import { LocalVolumeMetadata } from '@/data/translator/db/personal';

import LocalVolumeList from './LocalVolumeList.vue';

const props = defineProps<{ type: 'gpt' | 'sakura' }>();

const message = useMessage();
const setting = useSettingStore();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const localVolumeListRef = ref<InstanceType<typeof LocalVolumeList>>();

const calculateFinished = (volume: LocalVolumeMetadata) =>
  volume.toc.filter((it) => {
    let chapterGlossaryId: string | undefined;
    if (props.type === 'gpt') {
      chapterGlossaryId = it.gpt;
    } else {
      chapterGlossaryId = it.sakura;
    }
    return chapterGlossaryId === volume.glossaryId;
  }).length;

const calculateExpired = (volume: LocalVolumeMetadata) =>
  volume.toc.filter((it) => {
    let chapterGlossaryId: string | undefined;
    if (props.type === 'gpt') {
      chapterGlossaryId = it.gpt;
    } else {
      chapterGlossaryId = it.sakura;
    }
    return (
      chapterGlossaryId !== undefined && chapterGlossaryId !== volume.glossaryId
    );
  }).length;

const queueAllVolumes = (volumes: LocalVolumeMetadata[]) => {
  volumes.forEach((volume) => queueVolume(volume.id));
};

const queueVolume = (volumeId: string) => {
  const task = buildPersonalTranslateTask(volumeId, {
    start: 0,
    end: 65535,
    expire: true,
  });

  const addJob =
    props.type === 'gpt' ? gptWorkspace.addJob : sakuraWorkspace.addJob;

  const success = addJob({
    task,
    description: volumeId,
    createAt: Date.now(),
  });

  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};

const downloadVolume = async (volumeId: string) => {
  const { mode } = setting.downloadFormat;

  try {
    const { filename, blob } =
      await PersonalVolumesManager.makeTranslationVolumeFile({
        volumeId,
        lang: mode,
        translationsMode: 'priority',
        translations: [props.type],
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

const showGlossaryModal = ref(false);
const selectedVolumeToEditGlossary = ref<LocalVolumeMetadata>();
const submitGlossary = (
  volumeId: string,
  glossary: { [key: string]: string }
) =>
  PersonalVolumesManager.updateGlossary(volumeId, toRaw(glossary))
    .then(() => message.success('术语表提交成功'))
    .catch((error) => message.error(`术语表提交失败：${error}`))
    .then(() => {});
</script>

<template>
  <local-volume-list
    ref="localVolumeListRef"
    :options="{ 全部排队: queueAllVolumes }"
    :beforeVolumeAdd="(file:File)=>queueVolume(file.name)"
  >
    <template #extra>
      <n-flex align="baseline" :wrap="false">
        <n-text style="white-space: nowrap">语言</n-text>
        <n-radio-group v-model:value="setting.downloadFormat.mode" size="small">
          <n-radio-button
            v-for="option in downloadModeOptions"
            :key="option.value"
            :value="option.value"
            :label="option.label"
          />
        </n-radio-group>
      </n-flex>
    </template>

    <template #volume="volume">
      <n-flex :size="4" vertical>
        <n-text>{{ volume.id }}</n-text>

        <n-text depth="3">
          <n-time :time="volume.createAt" type="relative" /> / 总计
          {{ volume.toc.length }} / 完成 {{ calculateFinished(volume) }} / 过期
          {{ calculateExpired(volume) }}
        </n-text>

        <n-flex :size="8">
          <c-button
            label="排队"
            size="tiny"
            secondary
            @click="queueVolume(volume.id)"
          />

          <c-button
            v-if="volume.id.endsWith('.txt')"
            label="阅读"
            tag="a"
            :href="`/workspace/reader/${encodeURIComponent(volume.id)}/0`"
            size="tiny"
            secondary
          />
          <c-button
            label="下载"
            size="tiny"
            secondary
            @click="downloadVolume(volume.id)"
          />

          <c-button
            :label="`术语表[${Object.keys(volume.glossary).length}]`"
            size="tiny"
            secondary
            @click="
              () => {
                selectedVolumeToEditGlossary = volume;
                showGlossaryModal = true;
              }
            "
          />

          <n-popconfirm
            :show-icon="false"
            @positive-click="localVolumeListRef?.deleteVolume(volume.id)"
            :negative-text="null"
          >
            <template #trigger>
              <c-button label="删除" type="error" size="tiny" secondary />
            </template>
            确定删除{{ volume.id }}吗？
          </n-popconfirm>
        </n-flex>
      </n-flex>
    </template>
  </local-volume-list>

  <n-divider style="margin-bottom: 4px" />

  <c-modal title="编辑术语表" v-model:show="showGlossaryModal">
    <n-p>{{ selectedVolumeToEditGlossary?.id }}</n-p>
    <glossary-edit
      v-if="selectedVolumeToEditGlossary !== undefined"
      :glossary="selectedVolumeToEditGlossary.glossary"
    />
    <template #action>
      <c-button
        label="提交"
        type="primary"
        @click="
          submitGlossary(
            selectedVolumeToEditGlossary!!.id,
            selectedVolumeToEditGlossary!!.glossary
          )
        "
      />
    </template>
  </c-modal>
</template>
