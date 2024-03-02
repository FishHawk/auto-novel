<script lang="ts" setup>
import { FileDownloadOutlined } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import {
  downloadModeOptions,
  downloadTranslationModeOptions,
  useSettingStore,
} from '@/data/stores/setting';
import {
  buildPersonalTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { PersonalVolumesManager, TranslatorId } from '@/data/translator';
import { LocalVolumeMetadata } from '@/data/translator/db/personal';

import LocalVolumeList from './LocalVolumeList.vue';

const message = useMessage();
const setting = useSettingStore();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const localVolumeListRef = ref<InstanceType<typeof LocalVolumeList>>();

const calculateFinished = (
  volume: LocalVolumeMetadata,
  translatorId: TranslatorId
) => volume.toc.filter((it) => it[translatorId]).length;

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (
  volumeId: string,
  translatorId: 'baidu' | 'youdao'
) =>
  translateTask?.value?.startTask(
    { type: 'personal', volumeId },
    {
      translateExpireChapter: true,
      syncFromProvider: false,
      startIndex: 0,
      endIndex: 65535,
    },
    { id: translatorId }
  );

const queueVolume = (volumeId: string, translatorId: 'gpt' | 'sakura') => {
  const task = buildPersonalTranslateTask(volumeId, {
    start: 0,
    end: 65535,
    expire: true,
  });

  const workspace = translatorId === 'gpt' ? gptWorkspace : sakuraWorkspace;

  const success = workspace.addJob({
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
  const { mode, translationsMode, translations } = setting.downloadFormat;

  try {
    const { filename, blob } =
      await PersonalVolumesManager.makeTranslationVolumeFile({
        volumeId,
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
</script>

<template>
  <local-volume-list ref="localVolumeListRef">
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

      <n-flex align="baseline" :wrap="false">
        <n-text style="white-space: nowrap">翻译</n-text>
        <n-flex>
          <n-radio-group
            v-model:value="setting.downloadFormat.translationsMode"
            size="small"
          >
            <n-radio-button
              v-for="option in downloadTranslationModeOptions"
              :key="option.value"
              :value="option.value"
              :label="option.label"
            />
          </n-radio-group>
          <translator-check
            v-model:value="setting.downloadFormat.translations"
            show-order
            size="small"
          />
        </n-flex>
      </n-flex>
    </template>

    <template #volume="volume">
      <n-flex align="center" justify="space-between" :wrap="false">
        <n-flex :size="4" vertical>
          <n-text>{{ volume.id }}</n-text>

          <n-text depth="3">
            总计 {{ volume.toc.length }} / 百度
            {{ calculateFinished(volume, 'baidu') }} / 有道
            {{ calculateFinished(volume, 'youdao') }} / GPT
            {{ calculateFinished(volume, 'gpt') }} / Sakura
            {{ calculateFinished(volume, 'sakura') }}
          </n-text>

          <n-flex :size="8">
            <c-button
              v-if="setting.enabledTranslator.includes('baidu')"
              label="更新百度"
              size="tiny"
              secondary
              @click="startTranslateTask(volume.id, 'baidu')"
            />
            <c-button
              v-if="setting.enabledTranslator.includes('youdao')"
              label="更新有道"
              size="tiny"
              secondary
              @click="startTranslateTask(volume.id, 'youdao')"
            />

            <c-button
              v-if="setting.enabledTranslator.includes('gpt')"
              label="排队GPT"
              size="tiny"
              secondary
              @click="queueVolume(volume.id, 'gpt')"
            />
            <c-button
              v-if="setting.enabledTranslator.includes('sakura')"
              label="排队Sakura"
              size="tiny"
              secondary
              @click="queueVolume(volume.id, 'sakura')"
            />

            <c-button
              v-if="volume.id.endsWith('.txt')"
              label="阅读"
              tag="a"
              :href="`/workspace/reader/${encodeURIComponent(volume.id)}/0`"
              size="tiny"
              secondary
            />

            <glossary-button :volume="volume" size="tiny" secondary />

            <n-popconfirm
              :show-icon="false"
              @positive-click="localVolumeListRef?.deleteVolume(volume.id)"
              :negative-text="null"
            >
              <template #trigger>
                <c-button label="删除" type="error" size="tiny" secondary />
              </template>
              真的要删除{{ volume.id }}吗？
            </n-popconfirm>
          </n-flex>
        </n-flex>
        <c-button
          label="下载"
          :icon="FileDownloadOutlined"
          async
          @click="downloadVolume(volume.id)"
        />
      </n-flex>
      <TranslateTask ref="translateTask" style="margin-top: 20px" />
      <!--
        TODO: 动态显示百度/有道变化
        @update:baidu="(zh) => (volume.baidu = zh)"
        @update:youdao="(zh) => (volume.youdao = zh)"
      -->
    </template>
  </local-volume-list>
</template>
