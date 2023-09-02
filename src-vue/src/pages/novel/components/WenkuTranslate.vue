<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWenkuNovel, VolumeJpDto } from '@/data/api/api_wenku_novel';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';
import { useSettingStore } from '@/data/stores/setting';

const props = defineProps<{
  novelId: string;
  glossary: { [key: string]: string };
  volumes: VolumeJpDto[];
}>();

const emits = defineEmits<{
  (e: 'update:baidu', v: number): void;
  (e: 'update:youdao', v: number): void;
  (e: 'update:gpt', v: number): void;
}>();

const setting = useSettingStore();
const gptAccessToken = ref('');
const gptAccessTokenOptions = computed(() => {
  return setting.openAiAccessTokens.map((t) => {
    return { label: t, value: t };
  });
});

interface TaskDetail {
  label: string;
  running: boolean;
  chapterTotal?: number;
  chapterFinished: number;
  chapterError: number;
  logs: string[];
}

const message = useMessage();

const taskDetail: Ref<TaskDetail | undefined> = ref();

async function startUpdateTask(volumeId: string, translatorId: TranslatorId) {
  if (taskDetail.value?.running) {
    message.info('已有任务在运行。');
    return;
  }

  const label = `${getTranslatorLabel(translatorId)}翻译`;
  taskDetail.value = {
    label,
    running: true,
    chapterFinished: 0,
    chapterError: 0,
    logs: [],
  };

  let accessToken = gptAccessToken.value.trim();
  try {
    const obj = JSON.parse(accessToken);
    accessToken = obj.accessToken;
  } catch {}

  await ApiWenkuNovel.translate(
    props.novelId,
    translatorId,
    volumeId,
    accessToken,
    {
      onStart: (total: number) => {
        taskDetail.value!.chapterTotal = total;
      },
      onChapterSuccess: (state) => {
        if (translatorId === 'baidu') {
          emits('update:baidu', state);
        } else if (translatorId === 'youdao') {
          emits('update:youdao', state);
        } else if (translatorId === 'gpt') {
          setting.addToken(gptAccessToken.value);
          emits('update:gpt', state);
        }
        taskDetail.value!.chapterFinished += 1;
      },
      onChapterFailure: () => (taskDetail.value!.chapterError += 1),
      log: (message: any) => {
        taskDetail.value!.logs.push(`${message}`);
      },
    }
  );

  taskDetail.value!.logs.push('\n结束');
  taskDetail.value!.running = false;
}

interface NovelFiles {
  label: string;
  translatorId: TranslatorId;
  files: { label: string; url: string; name: string }[];
}

function stateToFileList(volume: VolumeJpDto): NovelFiles[] {
  let ext: string;
  if (volume.volumeId.toLowerCase().endsWith('.txt')) {
    ext = 'txt';
  } else {
    ext = 'epub';
  }
  function createFile(
    label: string,
    lang:
      | 'zh-baidu'
      | 'zh-youdao'
      | 'zh-gpt'
      | 'mix-baidu'
      | 'mix-youdao'
      | 'mix-gpt'
  ) {
    return {
      label,
      url: ApiWenkuNovel.createFileUrl(props.novelId, volume.volumeId, lang),
      name: `${lang}.${ext}`,
    };
  }
  const extUpper = ext.toUpperCase();
  return [
    {
      label: `百度(${volume.baidu}/${volume.total})`,
      translatorId: 'baidu',
      files: [
        createFile(extUpper, 'zh-baidu'),
        createFile(`中日对比${extUpper}`, 'mix-baidu'),
      ],
    },
    {
      label: `有道(${volume.youdao}/${volume.total})`,
      translatorId: 'youdao',
      files: [
        createFile(extUpper, 'zh-youdao'),
        createFile(`中日对比${extUpper}`, 'mix-youdao'),
      ],
    },
    {
      label: `GPT3(${volume.gpt}/${volume.total})`,
      translatorId: 'gpt',
      files: [
        createFile(extUpper, 'zh-gpt'),
        createFile(`中日对比${extUpper}`, 'mix-gpt'),
      ],
    },
  ];
}

const showAdvanceOptions = ref(false);

async function submitGlossary() {
  const result = await ApiWenkuNovel.updateGlossary(
    props.novelId,
    props.glossary
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
}

function sortVolumesJp(volumes: VolumeJpDto[]) {
  return volumes.sort((a, b) => a.volumeId.localeCompare(b.volumeId));
}
</script>

<template>
  <n-text depth="3" style="font-size: 12px">
    # 翻译功能需要需要安装浏览器插件，参见
    <n-a href="/wiki/extension" target="_blank">插件使用说明</n-a>
  </n-text>
  <n-p>
    高级选项
    <n-switch
      :rubber-band="false"
      size="small"
      v-model:value="showAdvanceOptions"
    />
  </n-p>
  <n-collapse-transition :show="showAdvanceOptions" style="margin-bottom: 16px">
    <n-list bordered>
      <n-list-item>
        <GlossaryEdit :glossary="glossary" :submit="submitGlossary" />
      </n-list-item>
    </n-list>
  </n-collapse-transition>

  <n-auto-complete
    v-model:value="gptAccessToken"
    :options="gptAccessTokenOptions"
    placeholder="请输入GPT的Access Token"
    :get-show="() => true"
  />

  <template v-for="volume of sortVolumesJp(volumes)">
    <n-p>{{ volume.volumeId }}</n-p>
    <n-space v-for="row in stateToFileList(volume)" style="padding: 4px">
      <span>{{ row.label }}</span>
      <n-space>
        <n-a
          v-for="file in row.files"
          :href="file.url"
          :download="file.name"
          target="_blank"
        >
          {{ file.label }}
        </n-a>
      </n-space>
      <n-button
        size="tiny"
        @click="startUpdateTask(volume.volumeId, row.translatorId)"
        style="margin-left: 24px"
      >
        更新
      </n-button>
    </n-space>
  </template>

  <TranslateTaskDetail
    v-if="taskDetail"
    :label="taskDetail.label"
    :running="taskDetail.running"
    :chapter-total="taskDetail.chapterTotal"
    :chapter-finished="taskDetail.chapterFinished"
    :chapter-error="taskDetail.chapterError"
    :logs="taskDetail.logs"
    style="margin-top: 20px"
  />
</template>
