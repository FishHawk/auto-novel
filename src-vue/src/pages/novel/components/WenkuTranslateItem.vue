<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';
import { useSettingStore } from '@/data/stores/setting';

const props = defineProps<{
  novelId: string;
  volumeId: string;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
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

async function startUpdateTask(translatorId: TranslatorId) {
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
    props.volumeId,
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

function stateToFileList(): NovelFiles[] {
  let ext: string;
  if (props.volumeId.toLowerCase().endsWith('.txt')) {
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
      url: ApiWenkuNovel.createFileUrl(props.novelId, props.volumeId, lang),
      name: `${lang}.${ext}`,
    };
  }
  const extUpper = ext.toUpperCase();
  return [
    {
      label: `百度(${props.baidu}/${props.total})`,
      translatorId: 'baidu',
      files: [
        createFile(extUpper, 'zh-baidu'),
        createFile(`中日对比${extUpper}`, 'mix-baidu'),
      ],
    },
    {
      label: `有道(${props.youdao}/${props.total})`,
      translatorId: 'youdao',
      files: [
        createFile(extUpper, 'zh-youdao'),
        createFile(`中日对比${extUpper}`, 'mix-youdao'),
      ],
    },
    {
      label: `GPT3(${props.gpt}/${props.total})`,
      translatorId: 'gpt',
      files: [
        createFile(extUpper, 'zh-gpt'),
        createFile(`中日对比${extUpper}`, 'mix-gpt'),
      ],
    },
  ];
}
</script>

<template>
  <n-auto-complete
    v-model:value="gptAccessToken"
    :options="gptAccessTokenOptions"
    placeholder="请输入GPT的Access Token"
    :get-show="() => true"
  />
  <div v-for="row in stateToFileList()">
    <n-space style="padding: 4px">
      <n-text>{{ row.label }}</n-text>
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
        @click="startUpdateTask(row.translatorId)"
        style="margin-left: 24px"
      >
        更新
      </n-button>
    </n-space>
  </div>

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
