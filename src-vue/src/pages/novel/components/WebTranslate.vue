<script lang="ts" setup>
import { computed, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWebNovel } from '@/data/api/api_web_novel';
import { TaskCallback } from '@/data/api/api_web_novel_translate';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';
import { useIsDesktop } from '@/data/util';
import { useSettingStore } from '@/data/stores/setting';
import { useAuthInfoStore } from '@/data/stores/authInfo';

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
const showAdvanceOptions = ref(false);

const authInfoStore = useAuthInfoStore();
const termsToAdd = ref<[string, string]>(['', '']);

function deleteTerm(jp: string) {
  delete props.glossary[jp];
}

function addTerm() {
  const [jp, zh] = termsToAdd.value;
  if (jp && zh) {
    props.glossary[jp] = zh;
    termsToAdd.value = ['', ''];
  }
}

async function submitGlossary() {
  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }
  const patch = {
    glossary: props.glossary,
    toc: {},
  };
  const result = await ApiWebNovel.putMetadata(
    props.providerId,
    props.novelId,
    patch,
    token
  );
  if (result.ok) {
    message.success('术语表提交成功');
  } else {
    message.error('术语表提交失败：' + result.error.message);
  }
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
      高级选项
      <n-switch
        :rubber-band="false"
        size="small"
        v-model:value="showAdvanceOptions"
      />
    </n-p>
    <n-collapse-transition
      :show="showAdvanceOptions"
      style="margin-bottom: 16px"
    >
      <n-list bordered>
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
          <n-thing title="术语表">
            <template #description>
              术语表过大可能会使得翻译质量下降（例如：百度/有道将无法从判断人名性别，导致人称代词错误），请不要过度依赖术语表。
              <br />
              术语表修改后，再次更新翻译时，已翻译章节会重新翻译有变化的段落，尽量避免频繁编辑。
              <br />
              GPT暂不支持。
            </template>
            <n-p>
              <n-input-group style="max-width: 400px">
                <n-input
                  pair
                  v-model:value="termsToAdd"
                  separator="=>"
                  :placeholder="['日文', '中文']"
                />
                <n-button @click="addTerm()">添加</n-button>
                <AsyncButton :on-async-click="submitGlossary">提交</AsyncButton>
              </n-input-group>
            </n-p>

            <n-scrollbar style="max-height: 400px">
              <table style="border-spacing: 16px 0px">
                <tr v-for="(termZh, termJp) in glossary">
                  <td>{{ termJp }}</td>
                  <td>=></td>
                  <td>{{ termZh }}</td>
                  <td>
                    <n-button size="tiny" @click="deleteTerm(termJp as string)">
                      删除
                    </n-button>
                  </td>
                </tr>
              </table>
            </n-scrollbar>
          </n-thing>
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
