<script lang="ts" setup>
import { FileDownloadFilled } from '@vicons/material';
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { ApiSakura } from '@/data/api/api_sakura';
import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { useSettingStore } from '@/data/stores/setting';
import { useUserDataStore } from '@/data/stores/user_data';
import {
  buildWenkuTranslateTask,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { TranslatorId } from '@/data/translator/translator';

const { novelId, volume, getParams } = defineProps<{
  novelId: string;
  volume: VolumeJpDto;
  getParams: () => { translateExpireChapter: boolean };
}>();

const emit = defineEmits<{ delete: [] }>();

const message = useMessage();
const userData = useUserDataStore();
const setting = useSettingStore();
const gptWorkspace = useGptWorkspaceStore();
const sakuraWorkspace = useSakuraWorkspaceStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const startTranslateTask = (translatorId: 'baidu' | 'youdao') => {
  const params = getParams();
  return translateTask?.value?.startTask(
    { type: 'wenku', novelId, volumeId: volume.volumeId },
    {
      ...params,
      syncFromProvider: false,
      startIndex: 0,
      endIndex: 65536,
    },
    { id: translatorId }
  );
};

const translatorLabels = computed(
  () =>
    <{ label: string; translatorId: TranslatorId }[]>[
      {
        label: `百度(${volume.baidu}/${volume.total})`,
        translatorId: 'baidu',
      },
      {
        label: `有道(${volume.youdao}/${volume.total})`,
        translatorId: 'youdao',
      },
      {
        label: `GPT3(${volume.gpt}/${volume.total})`,
        translatorId: 'gpt',
      },
      {
        label: `Sakura(${volume.sakura}/${volume.total})`,
        translatorId: 'sakura',
      },
    ]
);

const file = computed(() => {
  const { mode, translationsMode, translations } = setting.downloadFormat;

  let lang: 'zh' | 'zh-jp' | 'jp-zh';
  if (mode === 'jp' || mode === 'zh') {
    lang = 'zh';
  } else if (mode === 'mix') {
    lang = 'zh-jp';
  } else {
    lang = 'jp-zh';
  }

  const { url, filename } = ApiWenkuNovel.createFileUrl({
    novelId,
    volumeId: volume.volumeId,
    lang,
    translationsMode,
    translations,
  });
  return { url, filename };
});

const submitPublicSakuraJob = async () => {
  const { translateExpireChapter } = getParams();
  const result = await ApiSakura.createSakuraJobWenkuTranslate(
    novelId,
    volume.volumeId,
    {
      start: 0,
      end: 65535,
      expire: translateExpireChapter,
    }
  );
  if (result.ok) {
    message.info('排队成功');
  } else {
    message.error('排队失败:' + result.error.message);
  }
};

const submitJob = (id: 'gpt' | 'sakura') => {
  const { translateExpireChapter } = getParams();
  const task = buildWenkuTranslateTask(novelId, volume.volumeId, {
    start: 0,
    end: 65535,
    expire: translateExpireChapter,
  });
  const workspace = id === 'gpt' ? gptWorkspace : sakuraWorkspace;
  const success = workspace.addJob({
    task,
    description: volume.volumeId,
    createAt: Date.now(),
  });
  if (success) {
    message.success('排队成功');
  } else {
    message.error('排队失败：翻译任务已经存在');
  }
};
</script>

<template>
  <div>
    <n-space align="center" justify="space-between" :wrap="false">
      <n-space vertical>
        <n-text>{{ volume.volumeId }}</n-text>
        <n-space>
          <template v-for="{ translatorId, label } of translatorLabels">
            <n-button
              v-if="translatorId === 'gpt' || translatorId === 'sakura'"
              text
              type="primary"
              @click="() => submitJob(translatorId)"
            >
              排队{{ label }}
            </n-button>

            <n-button
              v-else
              text
              type="primary"
              @click="startTranslateTask(translatorId)"
            >
              更新{{ label }}
            </n-button>
          </template>

          <async-button
            text
            type="primary"
            @async-click="submitPublicSakuraJob"
          >
            公用排队Sakura
          </async-button>

          <n-popconfirm
            v-if="userData.isMaintainer"
            :show-icon="false"
            @positive-click="emit('delete')"
            :negative-text="null"
          >
            <template #trigger>
              <n-button text type="error"> 删除 </n-button>
            </template>
            真的要删除《{{ volume.volumeId }}》吗？
          </n-popconfirm>
        </n-space>
      </n-space>

      <n-a :href="file.url" :download="file.filename" target="_blank">
        <n-button>
          <template #icon>
            <n-icon :component="FileDownloadFilled" />
          </template>
          下载
        </n-button>
      </n-a>
    </n-space>

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
