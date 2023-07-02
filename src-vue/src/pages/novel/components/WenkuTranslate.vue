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
}>();

const emits = defineEmits<{
  (e: 'update:baidu', v: number): void;
  (e: 'update:youdao', v: number): void;
}>();

interface UpdateProgress {
  name: string;
  total?: number;
  finished: number;
  error: number;
}

const message = useMessage();

const progress: Ref<UpdateProgress | undefined> = ref();

async function startUpdateTask(translatorId: TranslatorId) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }

  const name = `${getTranslatorLabel(translatorId)}翻译`;
  progress.value = {
    name,
    finished: 0,
    error: 0,
  };

  const result = await ApiWenkuNovel.translate(
    props.novelId,
    translatorId,
    props.volumeId,
    {
      onStart: (total: number) => {
        progress.value!.total = total;
      },
      onChapterTranslateSuccess: (state) => {
        if (translatorId === 'baidu') {
          emits('update:baidu', state);
        } else {
          emits('update:youdao', state);
        }
        progress.value!.finished += 1;
      },
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
  translatorId: 'baidu' | 'youdao';
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
    lang: 'zh-baidu' | 'zh-youdao' | 'mix-baidu' | 'mix-youdao'
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
  ];
}
</script>

<template>
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
