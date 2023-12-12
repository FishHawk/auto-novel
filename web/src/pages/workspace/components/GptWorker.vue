<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { client } from '@/data/api/client';
import { GptWorker, useGptWorkspaceStore } from '@/data/stores/workspace';

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

const realEndpoint = computed(() => {
  if (worker.endpoint.length === 0) {
    if (worker.type === 'web') {
      return 'https://chatgpt-proxy.lss233.com/api';
    } else {
      return 'https://api.openai.com/v1';
    }
  } else {
    return worker.endpoint;
  }
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
    const task = job.task;

    const [taskString, queryString] = task.split('?');
    const { start, end, expire } = Object.fromEntries(
      new URLSearchParams(queryString)
    );

    let desc: any;
    if (taskString.startsWith('web')) {
      const [type, providerId, novelId] = taskString.split('/');
      desc = { type, providerId, novelId };
    } else if (taskString.startsWith('wenku')) {
      const [type, novelId, volumeId] = taskString.split('/');
      desc = { type, novelId, volumeId };
    } else if (taskString.startsWith('personal')) {
      const [type, volumeId] = taskString.split('/');
      desc = { type, volumeId };
    }

    if (desc === undefined) continue;

    const parseIntWithDefault = (str: string, defaultValue: number) => {
      const num = parseInt(str, 10);
      return isNaN(num) ? defaultValue : num;
    };

    const completed = await translateTask.value!!.startTask(
      desc,
      {
        translateExpireChapter: expire === 'true',
        syncFromProvider: false,
        startIndex: parseIntWithDefault(start, 0),
        endIndex: parseIntWithDefault(end, 65535),
      },
      {
        id: 'gpt',
        type: worker.type,
        endpoint: realEndpoint.value,
        key: worker.key,
      },
      {
        onProgressUpdated: (progress) => {
          emit('update:progress', task, {
            state: 'processed',
            ...progress,
          });
        },
      }
    );
    emit('update:progress', task, { state: 'finish' });

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
      id: 'gpt',
      client,
      glossary: {},
      type: worker.type,
      endpoint: realEndpoint.value,
      key: worker.key,
      log: () => {},
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
        {{ worker.type === 'web' ? 'Web' : 'Api' }}-{{
          worker.key.substring(0, 7)
        }}@{{ realEndpoint }}
      </n-text>
    </template>

    <template #description>
      <n-p v-if="currentJob">
        {{ currentJob.description }}
      </n-p>
    </template>

    <template #header-extra>
      <n-space :wrap="false">
        <async-button
          size="tiny"
          secondary
          @async-click="() => testGptWorker()"
        >
          测试
        </async-button>
        <n-button
          v-if="running"
          size="tiny"
          secondary
          @click="() => stopGptWorker()"
        >
          暂停
        </n-button>
        <n-button v-else size="tiny" secondary @click="() => startGptWorker()">
          启动
        </n-button>
        <n-button
          size="tiny"
          secondary
          type="error"
          @click="() => deleteGptWorker()"
        >
          删除
        </n-button>
      </n-space>
    </template>
  </n-thing>

  <TranslateTask ref="translateTask" style="margin-top: 20px" />
</template>
