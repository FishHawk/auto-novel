<script lang="ts" setup>
import { computed, onMounted, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { useWindowSize } from '@vueuse/core';

import { ResultState } from '@/data/api/result';
import ApiWenkuNovel, { ChapterStateDto } from '@/data/api/api_wenku_novel';

const props = defineProps<{
  novelId: string;
  volumeId: string;
}>();

interface UpdateProgress {
  name: string;
  total?: number;
  finished: number;
  error: number;
}

const message = useMessage();

const novelState = ref<ResultState<ChapterStateDto[]>>();
const progress: Ref<UpdateProgress | undefined> = ref();

onMounted(() => getState());
let lastPoll = false;
async function getState() {
  const result = await ApiWenkuNovel.getTranslateState(
    props.novelId,
    props.volumeId
  );

  if (result.ok) {
    novelState.value = result;
  }

  if (progress.value || lastPoll) {
    lastPoll = progress.value !== undefined;
    window.setTimeout(() => getState(), 2000);
  }
}

async function startUpdateTask(
  version: 'baidu' | 'youdao',
  startIndex: number,
  endIndex: number
) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  let name;
  if (version === 'baidu') {
    name = '百度翻译';
  } else {
    name = '有道翻译';
  }

  progress.value = {
    name,
    finished: 0,
    error: 0,
  };

  const result = await ApiWenkuNovel.update(
    version,
    props.novelId,
    props.volumeId,
    {
      onStart: (total: number) => {
        progress.value!.total = total;
        getState();
      },
      onChapterTranslateSuccess: () => (progress.value!.finished += 1),
      onChapterTranslateFailure: () => (progress.value!.error += 1),
    }
  );

  if (result.ok) {
    const total = progress.value.total;
    if (total && total > 0) {
      const progressHint = `${progress.value?.finished}/${progress.value?.total}`;
      message.success(`${name}任务完成:[${progressHint}]`);
    } else {
      message.success(`${name}任务完成:没有需要更新的章节`);
    }
  } else {
    console.log(result.error);
    message.error(`${name}任务失败:${result.error.message}`);
  }
  progress.value = undefined;
}

interface NovelFiles {
  label: string;
  version: 'baidu' | 'youdao';
  files: { label: string; url: string; name: string }[];
}

function stateToFileList(): NovelFiles[] {
  function createFile(label: string, lang: string) {
    return {
      label,
      url: `/api/wenku/non-archived/prepare-book/${props.volumeId}/${lang}`,
      name: `${lang}.epub`,
    };
  }

  let state: ChapterStateDto[] | undefined;
  if (novelState.value?.ok) {
    state = novelState.value.value;
  } else {
    state = undefined;
  }

  const jp = state?.length ?? '-';
  const baidu = state?.filter((it) => it.baidu)?.length ?? '-';
  const youdao = state?.filter((it) => it.youdao)?.length ?? '-';

  return [
    {
      label: `百度(${baidu}/${jp})`,
      version: 'baidu',
      files: [
        createFile('EPUB', 'zh-baidu'),
        createFile('中日对比EPUB', 'mix-baidu'),
      ],
    },
    {
      label: `有道(${youdao}/${jp})`,
      version: 'youdao',
      files: [
        createFile('EPUB', 'zh-youdao'),
        createFile('中日对比EPUB', 'mix-youdao'),
      ],
    },
  ];
}
</script>

<template>
  <div v-for="row in stateToFileList()">
    <n-space style="padding: 8px">
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
        tertiary
        size="small"
        @click="startUpdateTask(row.version, 0, 65536)"
        style="margin-left: 24px"
      >
        更新
      </n-button>
    </n-space>
  </div>

  <div v-if="progress !== undefined">
    <n-space
      v-if="progress !== undefined"
      align="center"
      justify="space-between"
      style="width: 100%"
    >
      <span>{{ progress.name }}</span>
      <div>
        <span>成功:{{ progress.finished ?? '-' }}</span>
        <n-divider vertical />
        <span>失败:{{ progress.error ?? '-' }}</span>
        <n-divider vertical />
        <span>总共:{{ progress.total ?? '-' }}</span>
      </div>
    </n-space>
    <n-progress
      type="line"
      :percentage="
        Math.round(
          (1000 * (progress.finished + progress.error)) / (progress.total ?? 1)
        ) / 10
      "
      style="width: 100%"
    />
  </div>
</template>
