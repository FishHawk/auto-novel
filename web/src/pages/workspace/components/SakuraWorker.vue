<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { client } from '@/data/api/client';
import { useSakuraWorkspaceStore } from '@/data/stores/workspace';

import { parseTask } from './util';
import { SakuraTranslator } from '@/data/translator/translator_sakura';

const { id, endpoint, useLlamaApi, getNextJob } = defineProps<{
  id: string;
  endpoint: string;
  useLlamaApi: boolean;
  getNextJob: () =>
    | { task: string; description: string; createAt: number }
    | undefined;
}>();

const emit = defineEmits<{
  'update:progress': [
    string,
    (
      | { state: 'finish' }
      | { state: 'processed'; finished: number; error: number; total: number }
    )
  ];
}>();

const message = useMessage();
const sakuraWorkspace = useSakuraWorkspaceStore();

const translateTask = ref<InstanceType<typeof TranslateTask>>();
const currentJob = ref<{
  task: string;
  description: string;
  createAt: number;
}>();
const running = computed(() => currentJob.value !== undefined);

const processTasks = async () => {
  while (true) {
    const job = getNextJob();
    currentJob.value = job;

    if (job === undefined) break;
    const { desc, params } = parseTask(job.task);

    const completed = await translateTask.value!!.startTask(
      desc,
      params,
      {
        id: 'sakura',
        endpoint,
        useLlamaApi,
      },
      {
        onProgressUpdated: (progress) => {
          emit('update:progress', job.task, {
            state: 'processed',
            ...progress,
          });
        },
      }
    );
    emit('update:progress', job.task, { state: 'finish' });

    if (!completed) break;
  }
  currentJob.value = undefined;
};

const startSakuraWorker = () => {
  if (running.value) return;
  processTasks();
};
const stopSakuraWorker = () => {
  if (!running.value) return;
  // TODO
  message.error('还不支持');
};
const deleteSakuraWorker = () => {
  // TODO
  if (running.value) {
    message.error('翻译器正在运行');
    return;
  }
  sakuraWorkspace.deleteWorker(id);
};

const testSakuraWorker = async () => {
  const input =
    '国境の長いトンネルを抜けると雪国であった。夜の底が白くなった。信号所に汽車が止まった。';
  const Translator = (await import('@/data/translator')).Translator;
  try {
    const translator = await Translator.createWithoutCache({
      id: 'sakura',
      client,
      glossary: {},
      endpoint,
      useLlamaApi,
      log: () => {},
    });
    const result = await translator.translate([input]);
    const output = result[0];

    const segTranslator = translator.segTranslator as any as SakuraTranslator;
    console.log('模型指纹');
    console.log(segTranslator.model.fingerprint);
    message.success(
      [
        `原文：${input}`,
        `译文：${output}`,
        `版本：${segTranslator.model.version} ${
          segTranslator.allowUpload() ? '允许上传' : '禁止上传'
        }`,
      ].join('\n')
    );
  } catch (e: any) {
    message.error(`Sakura报错：${e}`);
  }
};
</script>

<template>
  <n-thing content-indented>
    <template #avatar>
      <n-icon-wrapper :size="12" :border-radius="0" style="margin-top: 5px" />
    </template>

    <template #header>
      {{ id }}
      <n-text depth="3" style="font-size: 12px; padding-left: 2px">
        {{ useLlamaApi ? '' : '旧版@' }}{{ endpoint }}
      </n-text>
    </template>

    <template #description>
      <n-p v-if="currentJob">
        {{ currentJob.description }}
      </n-p>
    </template>

    <template #header-extra>
      <n-flex :wrap="false">
        <c-button
          label="测试"
          async
          size="tiny"
          secondary
          @click="testSakuraWorker"
        />

        <c-button
          v-if="running"
          label="暂停"
          size="tiny"
          secondary
          @click="stopSakuraWorker"
        />
        <c-button
          v-else
          label="启动"
          size="tiny"
          secondary
          @click="startSakuraWorker"
        />

        <c-button
          label="删除"
          size="tiny"
          secondary
          type="error"
          @click="deleteSakuraWorker"
        />
      </n-flex>
    </template>
  </n-thing>

  <TranslateTask ref="translateTask" style="margin-top: 20px" />
</template>
