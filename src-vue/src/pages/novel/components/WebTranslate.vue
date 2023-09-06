<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWebNovel } from '@/data/api/api_web_novel';
import { TaskCallback } from '@/data/api/api_web_novel_translate';
import { useSettingStore } from '@/data/stores/setting';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';
import { useIsDesktop } from '@/data/util';

const props = defineProps<{
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
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
const isDesktop = useIsDesktop(600);
const message = useMessage();

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

const startIndex = ref<number | null>(0);
const endIndex = ref<number | null>(65536);
const taskDetail: Ref<TaskDetail | undefined> = ref();

async function startTask(translatorId: TranslatorId | 'check-update') {
  if (taskDetail.value?.running) {
    message.info('已有任务在运行。');
    return;
  }
  taskDetail.value = {
    label: '',
    running: true,
    chapterFinished: 0,
    chapterError: 0,
    logs: [],
  };

  const callback: TaskCallback = {
    onStart: (total) => {
      taskDetail.value!.chapterTotal = total;
    },
    onChapterSuccess: (state) => {
      if (state.jp) emits('update:jp', state.jp);
      if (state.baidu) emits('update:baidu', state.baidu);
      if (state.youdao) emits('update:youdao', state.youdao);
      if (state.gpt) {
        setting.addToken(gptAccessToken.value);
        emits('update:gpt', state.gpt);
      }
      taskDetail.value!.chapterFinished += 1;
    },
    onChapterFailure: () => {
      taskDetail.value!.chapterError += 1;
    },
    log: (message: any) => {
      taskDetail.value!.logs.push(`${message}`);
    },
  };

  if (translatorId === 'check-update') {
    taskDetail.value.label = '检查更新';

    await ApiWebNovel.checkUpdate(
      props.providerId,
      props.novelId,
      startIndex.value ?? 0,
      endIndex.value ?? 65536,
      callback
    );
  } else {
    taskDetail.value.label = `${getTranslatorLabel(translatorId)}翻译`;

    let accessToken = gptAccessToken.value.trim();
    try {
      const obj = JSON.parse(accessToken);
      accessToken = obj.accessToken;
    } catch {}

    await ApiWebNovel.translate(
      props.providerId,
      props.novelId,
      translatorId,
      accessToken,
      startIndex.value ?? 0,
      endIndex.value ?? 65536,
      callback
    );
  }

  taskDetail.value!.logs.push('\n结束');
  taskDetail.value!.running = false;
}

interface NovelFiles {
  label: string;
  translatorId?: TranslatorId;
  files: { label: string; url: string; name: string }[];
}

function stateToFileList(): NovelFiles[] {
  let title: string;
  if (setting.downloadFilenameType === 'jp') {
    title = props.titleJp;
  } else {
    title = props.titleZh ?? props.titleJp;
  }
  const validTitle = title.replace(/[\/|\\:*?"<>]/g, '');

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

const showAdvanceOptions = ref(false);

const downloadFilenameTypeOptions = [
  { value: 'jp', label: '日文' },
  { value: 'zh', label: '中文' },
];

async function submitGlossary() {
  const result = await ApiWebNovel.updateGlossary(
    props.providerId,
    props.novelId,
    props.glossary
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
}
</script>

<template>
  <n-text depth="3" style="font-size: 12px">
    # 翻译功能需要需要安装浏览器插件，参见
    <n-a href="/forum/64f3d63f794cbb1321145c07">插件使用说明</n-a>
  </n-text>
  <n-p>
    高级选项
    <n-switch
      :rubber-band="false"
      size="small"
      v-model:value="showAdvanceOptions"
    />
  </n-p>
  <n-collapse-transition :show="showAdvanceOptions" style="margin-bottom: 16px">
    <n-list bordered>
      <n-list-item>
        <n-thing title="下载文件名">
          <n-radio-group v-model:value="setting.downloadFilenameType">
            <n-space>
              <n-radio
                v-for="option in downloadFilenameTypeOptions"
                :key="option.value"
                :value="option.value"
              >
                {{ option.label }}
              </n-radio>
            </n-space>
          </n-radio-group>
        </n-thing>
      </n-list-item>

      <n-list-item>
        <n-thing title="自定义更新范围">
          <template #description>
            控制翻译任务的范围，章节序号可以看下面目录结尾方括号里的数字。
            <br />
            比如，从0到10，表示章节需要属于区间[0，10)的章节，不包含序号10。
          </template>
          <n-input-group>
            <n-input-group-label>从</n-input-group-label>
            <n-input-number
              v-model:value="startIndex"
              :min="0"
              style="width: 120px"
            />
            <n-input-group-label>到</n-input-group-label>
            <n-input-number
              v-model:value="endIndex"
              :min="0"
              style="width: 120px"
            />
          </n-input-group>
        </n-thing>
      </n-list-item>

      <n-list-item>
        <n-thing title="检查章节更新">
          <template #description>
            如果缓存的章节和源网站不匹配，则删除缓存章节。支持自定义范围。
            <br />
            删除章节也会删除翻译，尤其是GPT翻译，请谨慎使用。
          </template>

          <n-button @click="startTask('check-update')">检查更新</n-button>
        </n-thing>
      </n-list-item>

      <n-list-item>
        <GlossaryEdit :glossary="glossary" :submit="submitGlossary" />
      </n-list-item>
    </n-list>
  </n-collapse-transition>

  <n-auto-complete
    v-model:value="gptAccessToken"
    :options="gptAccessTokenOptions"
    placeholder="请输入GPT的Access Token"
    :get-show="() => true"
  />

  <n-list>
    <n-list-item v-for="row in stateToFileList()">
      <template #suffix>
        <n-button
          v-if="row.translatorId"
          tertiary
          size="small"
          @click="startTask(row.translatorId)"
        >
          {{ getTranslatorLabel(row.translatorId) }}翻译
        </n-button>
      </template>
      <n-space :vertical="!isDesktop">
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
</template>
