<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { client } from '@/data/api/client';
import { GptWorker, useGptWorkspaceStore } from '@/data/stores/workspace';
import { TranslatorDesc } from '@/data/translator/api';

import { parseTask } from './util';

const { worker, getNextJob } = defineProps<{
  worker: GptWorker;
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
const gptWorkspace = useGptWorkspaceStore();

const translatorDesc = computed<TranslatorDesc & { id: 'gpt' }>(() => {
  const endpoint = (() => {
    if (worker.endpoint.length === 0) {
      if (worker.type === 'web') {
        return 'https://chatgpt-proxy.lss233.com/api';
      } else {
        return 'https://api.openai.com';
      }
    } else {
      return worker.endpoint;
    }
  })();
  return {
    id: 'gpt',
    type: worker.type,
    model: worker.model ?? 'gpt-3.5',
    endpoint,
    key: worker.key,
  };
});

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
      translatorDesc.value,
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

const startGptWorker = () => {
  if (running.value) return;
  processTasks();
};
const stopGptWorker = () => {
  if (!running.value) return;
  // TODO
  message.error('还不支持');
};
const deleteGptWorker = () => {
  // TODO
  if (running.value) {
    message.error('翻译器正在运行');
    return;
  }
  gptWorkspace.deleteWorker(worker.id);
};

const testGptWorker = async () => {
  const input =
    '国境の長いトンネルを抜けると雪国であった。夜の底が白くなった。信号所に汽車が止まった。';
  const Translator = (await import('@/data/translator')).Translator;
  try {
    const translator = await Translator.createWithoutCache({
      client,
      glossary: {},
      log: () => {},
      ...translatorDesc.value,
    });
    const result = await translator.translate([input]);
    const output = result[0];
    message.success(`原文：${input}\n译文：${output}`);
  } catch (e: any) {
    message.error(`GPT报错：${e}`);
  }
};
</script>

<template>
  <n-thing content-indented>
    <template #avatar>
      <n-icon-wrapper :size="12" :border-radius="0" style="margin-top: 5px" />
    </template>

    <template #header>
      {{ worker.id }}
      <n-text depth="3" style="font-size: 12px; padding-left: 2px">
        {{
          `${translatorDesc.type}-${translatorDesc.model}[${worker.key.slice(
            -4
          )}]@${translatorDesc.endpoint}`
        }}
      </n-text>
    </template>

    <template #description>
      <n-p v-if="currentJob">
        {{ currentJob.description }}
      </n-p>
    </template>

    <template #header-extra>
      <n-flex :wrap="false">
        <async-button
          size="tiny"
          secondary
          @async-click="() => testGptWorker()"
        >
          测试
        </async-button>
        <n-button
          v-if="running"
          round
          size="tiny"
          secondary
          @click="() => stopGptWorker()"
        >
          暂停
        </n-button>
        <n-button
          v-else
          round
          size="tiny"
          secondary
          @click="() => startGptWorker()"
        >
          启动
        </n-button>
        <n-button
          round
          size="tiny"
          secondary
          type="error"
          @click="() => deleteGptWorker()"
        >
          删除
        </n-button>
      </n-flex>
    </template>
  </n-thing>

  <TranslateTask ref="translateTask" style="margin-top: 20px" />
</template>
