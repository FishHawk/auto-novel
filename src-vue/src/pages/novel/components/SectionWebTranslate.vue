<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWebNovel } from '@/data/api/api_web_novel';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';
import { useIsDesktop } from '@/data/util';
import { useSettingStore } from '@/data/stores/setting';

const isDesktop = useIsDesktop(600);

const message = useMessage();

const props = defineProps<{
  providerId: string;
  novelId: string;
  title: string;
  total: number;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  glossary: { [key: string]: string };
}>();

const emits = defineEmits<{
  (e: 'update:jp', v: number): void;
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

const startIndex = ref<number | null>(1);
const endIndex = ref<number | null>(65536);
const taskDetail: Ref<TaskDetail | undefined> = ref();

async function startUpdateTask(translatorId: TranslatorId) {
  if (taskDetail.value?.running) {
    message.info('已有任务在运行。');
    return;
  }

  const label = `${getTranslatorLabel(translatorId)}翻译`;
  taskDetail.value = {
    label: label,
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

  const result = await ApiWebNovel.translate(
    props.providerId,
    props.novelId,
    translatorId,
    accessToken,
    (startIndex.value ?? 1) - 1,
    (endIndex.value ?? 65536) - 1,
    {
      onStart: (total: number) => {
        taskDetail.value!.chapterTotal = total;
      },
      onChapterTranslateSuccess: (state) => {
        emits('update:jp', state.jp);
        if (translatorId === 'baidu') {
          emits('update:baidu', state.zh);
        } else if (translatorId === 'youdao') {
          emits('update:youdao', state.zh);
        } else if (translatorId === 'gpt') {
          setting.addToken(gptAccessToken.value);
          emits('update:gpt', state.zh);
        }
        taskDetail.value!.chapterFinished += 1;
      },
      onChapterTranslateFailure: () => {
        taskDetail.value!.chapterError += 1;
      },
      log: (message: any) => {
        taskDetail.value!.logs.push(`${message}`);
      },
    }
  );
  taskDetail.value!.logs.push('结束');

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
  taskDetail.value!.running = false;
}

interface NovelFiles {
  label: string;
  translatorId?: TranslatorId;
  files: { label: string; url: string; name: string }[];
}

function stateToFileList(): NovelFiles[] {
  const validTitle = props.title.replace(/[\/|\\:*?"<>]/g, '');
  function createFile(
    label: string,
    lang:
      | 'jp'
      | 'zh-baidu'
      | 'zh-youdao'
      | 'zh-gpt'
      | 'mix-baidu'
      | 'mix-youdao'
      | 'mix-gpt'
      | 'mix-all',
    type: 'epub' | 'txt'
  ) {
    return {
      label,
      url: ApiWebNovel.createFileUrl(
        props.providerId,
        props.novelId,
        lang,
        type
      ),
      name: `${props.providerId}.${props.novelId}.${lang}.${validTitle}.${type}`,
    };
  }

  return [
    {
      label: `日文(${props.jp}/${props.total})`,
      files: [createFile('TXT', 'jp', 'txt'), createFile('EPUB', 'jp', 'epub')],
    },
    {
      label: `百度(${props.baidu}/${props.total})`,
      translatorId: 'baidu',
      files: [
        createFile('TXT', 'zh-baidu', 'txt'),
        createFile('中日对比TXT', 'mix-baidu', 'txt'),
        createFile('EPUB', 'zh-baidu', 'epub'),
        createFile('中日对比EPUB', 'mix-baidu', 'epub'),
      ],
    },
    {
      label: `有道(${props.youdao}/${props.total})`,
      translatorId: 'youdao',
      files: [
        createFile('TXT', 'zh-youdao', 'txt'),
        createFile('中日对比TXT', 'mix-youdao', 'txt'),
        createFile('EPUB', 'zh-youdao', 'epub'),
        createFile('中日对比EPUB', 'mix-youdao', 'epub'),
      ],
    },
    {
      label: `GPT3(${props.gpt}/${props.total})`,
      translatorId: 'gpt',
      files: [
        createFile('TXT', 'zh-gpt', 'txt'),
        createFile('中日对比TXT', 'mix-gpt', 'txt'),
        createFile('EPUB', 'zh-gpt', 'epub'),
        createFile('中日对比EPUB', 'mix-gpt', 'epub'),
      ],
    },
    {
      label: `有道/百度`,
      files: [
        createFile('TXT', 'mix-all', 'txt'),
        createFile('EPUB', 'mix-all', 'epub'),
      ],
    },
  ];
}
</script>

<template>
  <section>
    <header><n-h2 prefix="bar">翻译</n-h2></header>

    <n-p>
      网页端翻译需要安装插件，请查看
      <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
      移动端暂时无法翻译。
    </n-p>
    <n-p>
      <n-collapse>
        <n-collapse-item title="高级模式">
          <n-p>自定义更新范围</n-p>
          <n-p style="padding-left: 16px">
            <n-input-group>
              <n-input-group-label>从</n-input-group-label>
              <n-input-number
                v-model:value="startIndex"
                :min="1"
                clearable
                style="width: 150px"
              />
              <n-input-group-label>到</n-input-group-label>
              <n-input-number
                v-model:value="endIndex"
                :min="1"
                clearable
                style="width: 150px"
              />
            </n-input-group>
          </n-p>

          <n-p>术语表[如果想编辑，请先进入编辑界面]</n-p>
          <n-p
            v-if="Object.keys(glossary).length === 0"
            style="margin-left: 16px"
          >
            还没设置术语表
          </n-p>
          <table style="border-spacing: 16px 0px">
            <tr v-for="(termZh, termJp) in glossary">
              <td>{{ termJp }}</td>
              <td>=></td>
              <td>{{ termZh }}</td>
            </tr>
          </table>
        </n-collapse-item>
      </n-collapse>
    </n-p>

    <n-auto-complete
      v-model:value="gptAccessToken"
      :options="gptAccessTokenOptions"
      placeholder="请输入GPT的Access Token"
      :get-show="() => true"
    />
    <n-table v-if="isDesktop" :bordered="false" :single-line="false">
      <thead>
        <tr>
          <th>版本</th>
          <th>链接</th>
          <th>更新</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="row in stateToFileList()">
          <td nowrap="nowrap">{{ row.label }}</td>
          <td>
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
          </td>
          <td>
            <n-button
              v-if="row.translatorId"
              tertiary
              size="small"
              @click="startUpdateTask(row.translatorId)"
            >
              更新{{ getTranslatorLabel(row.translatorId) }}
            </n-button>
          </td>
        </tr>
      </tbody>
    </n-table>

    <n-list v-else>
      <n-list-item v-for="row in stateToFileList()">
        <template #suffix>
          <n-button
            v-if="row.translatorId"
            tertiary
            size="small"
            @click="startUpdateTask(row.translatorId)"
          >
            更新{{ getTranslatorLabel(row.translatorId) }}
          </n-button>
        </template>
        <n-space vertical>
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
        </n-space>
      </n-list-item>
    </n-list>

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
  </section>
</template>
