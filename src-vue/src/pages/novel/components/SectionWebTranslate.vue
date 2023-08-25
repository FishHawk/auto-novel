<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWebNovel } from '@/data/api/api_web_novel';
import { TaskCallback } from '@/data/api/api_web_novel_translate';
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
      (startIndex.value ?? 1) - 1,
      (endIndex.value ?? 65536) - 1,
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
      (startIndex.value ?? 1) - 1,
      (endIndex.value ?? 65536) - 1,
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

    <n-text depth="3" style="font-size: 12px">
      # 翻译功能需要需要安装浏览器插件，参见
      <n-a href="/wiki/extension" target="_blank">插件使用说明</n-a>
    </n-text>
    <n-p>
      <n-collapse>
        <n-collapse-item title="高级模式">
          <n-list bordered>
            <n-list-item>
              <template #suffix>
                <n-input-group>
                  <n-input-group-label>从</n-input-group-label>
                  <n-input-number
                    v-model:value="startIndex"
                    :min="1"
                    clearable
                    style="width: 120px"
                  />
                  <n-input-group-label>到</n-input-group-label>
                  <n-input-number
                    v-model:value="endIndex"
                    :min="1"
                    clearable
                    style="width: 120px"
                  />
                </n-input-group>
              </template>
              <n-thing
                title="自定义更新范围"
                description="控制翻译任务的范围，章节序号可以看下面目录结尾方括号里的数字。"
              />
            </n-list-item>

            <n-list-item>
              <template #suffix>
                <n-button
                  tertiary
                  size="small"
                  @click="startTask('check-update')"
                >
                  检查更新
                </n-button>
              </template>
              <n-thing
                title="检查章节更新"
                description="如果缓存的章节和源网站不匹配，则删除章节。由于GPT翻译相当珍贵，所以不会检查有GPT翻译的章节。同样可以自定义范围。"
              />
            </n-list-item>

            <n-list-item>
              <n-thing
                title="术语表"
                description="如果想编辑术语表，请先进入编辑界面。"
              >
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
              </n-thing>
            </n-list-item>
          </n-list>
        </n-collapse-item>
      </n-collapse>
    </n-p>

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
