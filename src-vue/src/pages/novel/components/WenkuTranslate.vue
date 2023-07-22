<script lang="ts" setup>
import { Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWenkuNovel } from '@/data/api/api_wenku_novel';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';

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
}>();

interface TaskDetail {
  label: string;
  running: boolean;
  chapterTotal?: number;
  chapterFinished: number;
  chapterError: number;
  logs: string[];
}

const message = useMessage();

const gptAccessToken = ref('');
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

  const result = await ApiWenkuNovel.translate(
    props.novelId,
    translatorId,
    props.volumeId,
    gptAccessToken.value,
    {
      onStart: (total: number) => {
        taskDetail.value!.chapterTotal = total;
      },
      onChapterTranslateSuccess: (state) => {
        if (translatorId === 'baidu') {
          emits('update:baidu', state);
        } else {
          emits('update:youdao', state);
        }
        taskDetail.value!.chapterFinished += 1;
      },
      onChapterTranslateFailure: () => (taskDetail.value!.chapterError += 1),
      log: (message: any) => {
        taskDetail.value!.logs.push(`${message}`);
      },
    }
  );

  if (result.ok) {
    const total = taskDetail.value.chapterTotal;
    if (total && total > 0) {
      const progressHint = `${taskDetail.value?.chapterFinished}/${taskDetail.value?.chapterTotal}`;
      message.success(`${label}任务完成:[${progressHint}]`);
    } else {
      message.success(`${label}任务完成:没有需要更新的章节`);
    }
  } else {
    console.log(result.error);
    message.error(`${label}任务失败:${result.error.message}`);
  }
  taskDetail.value = undefined;
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
  <n-input
    v-model:value="gptAccessToken"
    type="textarea"
    placeholder="请输入GPT的Access Token"
  />
  <div v-for="row in stateToFileList()">
    <n-space style="padding: 4px">
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
