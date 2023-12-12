<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import { client } from '@/data/api/client';
import { useSakuraWorkspaceStore } from '@/data/stores/workspace';

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
    const task = job.task;

    const [taskString, queryString] = task.split('?');
    const { expire } = Object.fromEntries(new URLSearchParams(queryString));

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

    const completed = await translateTask.value!!.startTask(
      desc,
      {
        translateExpireChapter: expire === 'true',
        syncFromProvider: false,
        startIndex: 0,
        endIndex: 65535,
      },
      {
        id: 'sakura',
        endpoint,
        useLlamaApi,
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
    message.success(`原文：${input}\n译文：${output}`);
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
      <n-space :wrap="false">
        <async-button
          size="tiny"
          secondary
          @async-click="() => testSakuraWorker()"
        >
          测试
        </async-button>
        <n-button
          v-if="running"
          size="tiny"
          secondary
          @click="() => stopSakuraWorker()"
        >
          暂停
        </n-button>
        <n-button
          v-else
          size="tiny"
          secondary
          @click="() => startSakuraWorker()"
        >
          启动
        </n-button>
        <n-button
          size="tiny"
          secondary
          type="error"
          @click="() => deleteSakuraWorker()"
        >
          删除
        </n-button>
      </n-space>
    </template>
  </n-thing>

  <TranslateTask ref="translateTask" style="margin-top: 20px" />
</template>
