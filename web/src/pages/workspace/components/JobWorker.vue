<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed, ref } from 'vue';

import TranslateTask from '@/components/TranslateTask.vue';
import {
  GptWorker,
  SakuraWorker,
  useGptWorkspaceStore,
  useSakuraWorkspaceStore,
} from '@/data/stores/workspace';
import { Translator } from '@/data/translator';
import { TranslatorDesc } from '@/data/translator/api';
import { SakuraTranslator } from '@/data/translator/translator_sakura';

import { parseTask } from './util';

const { worker, getNextJob } = defineProps<{
  worker:
    | ({ translatorId: 'sakura' } & SakuraWorker)
    | ({ translatorId: 'gpt' } & GptWorker);
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
const workspace =
  worker.translatorId === 'gpt'
    ? useGptWorkspaceStore()
    : useSakuraWorkspaceStore();

const translatorDesc = computed<TranslatorDesc & { id: 'gpt' | 'sakura' }>(
  () => {
    if (worker.translatorId === 'gpt') {
      const endpoint = (() => {
        if (worker.endpoint.length === 0) {
          if (worker.type === 'web') {
            return 'https://chat.openai.com/backend-api';
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
    } else {
      return {
        id: 'sakura',
        endpoint: worker.endpoint,
        useLlamaApi: worker.useLlamaApi ?? false,
      };
    }
  }
);

const endpointPrefix = computed(() => {
  if (worker.translatorId === 'gpt') {
    return `${worker.type}-${worker.model ?? 'gpt-3.5'}[${worker.key.slice(
      -4
    )}]@`;
  } else {
    return worker.useLlamaApi ? '' : 'OAI@';
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

const startWorker = () => {
  if (running.value) return;
  processTasks();
};
const stopWorker = () => {
  if (!running.value) return;
  // TODO
  message.error('还不支持');
};
const deleteWorker = () => {
  // TODO
  if (running.value) {
    message.error('翻译器正在运行');
    return;
  }
  workspace.deleteWorker(worker.id);
};

const testWorker = async () => {
  const input =
    '国境の長いトンネルを抜けると雪国であった。夜の底が白くなった。信号所に汽車が止まった。';
  try {
    const translator = await Translator.create(
      {
        log: () => {},
        ...translatorDesc.value,
      },
      false
    );
    const result = await translator.translate([input], {});
    const output = result[0];

    if (worker.translatorId === 'gpt') {
      message.success(`原文：${input}\n译文：${output}`);
    } else {
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
    }
  } catch (e: any) {
    message.error(`翻译器错误：${e}`);
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
        {{ endpointPrefix }}{{ translatorDesc.endpoint }}
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
          @click="testWorker"
        />

        <c-button
          v-if="running"
          label="暂停"
          size="tiny"
          secondary
          @click="stopWorker"
        />
        <c-button
          v-else
          label="启动"
          size="tiny"
          secondary
          @click="startWorker"
        />

        <c-button
          label="删除"
          size="tiny"
          secondary
          type="error"
          @click="deleteWorker"
        />
      </n-flex>
    </template>
  </n-thing>

  <TranslateTask ref="translateTask" style="margin-top: 20px" />
</template>
