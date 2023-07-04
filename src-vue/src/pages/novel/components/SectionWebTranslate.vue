<script lang="ts" setup>
import { Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';

import { ApiWebNovel } from '@/data/api/api_web_novel';
import { getTranslatorLabel, TranslatorId } from '@/data/translator/translator';
import { useIsDesktop } from '@/data/util';

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
}>();

interface UpdateProgress {
  name: string;
  total?: number;
  finished: number;
  error: number;
}

const startIndex = ref<number | null>(1);
const endIndex = ref<number | null>(65536);
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

  const result = await ApiWebNovel.translate(
    props.providerId,
    props.novelId,
    translatorId,
    (startIndex.value ?? 1) - 1,
    (endIndex.value ?? 65536) - 1,
    {
      onStart: (total: number) => {
        progress.value!.total = total;
      },
      onChapterTranslateSuccess: (state) => {
        emits('update:jp', state.jp);
        if (translatorId === 'baidu') {
          emits('update:baidu', state.zh);
        } else {
          emits('update:youdao', state.zh);
        }
        progress.value!.finished += 1;
      },
      onChapterTranslateFailure: () => {
        progress.value!.error += 1;
      },
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
  translatorId?: 'baidu' | 'youdao';
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
      | 'mix-baidu'
      | 'mix-youdao'
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
      label: `有道/百度`,
      files: [
        createFile('TXT', 'mix-all', 'txt'),
        createFile('EPUB', 'mix-all', 'epub'),
      ],
    },
    {
      label: `GPT3(${props.gpt}/${props.total})`,
      files: [],
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
              @click="startUpdateTask(row.translatorId as any)"
            >
              更新
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
            @click="startUpdateTask(row.translatorId as any)"
          >
            更新
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
            (1000 * (progress.finished + progress.error)) /
              (progress.total ?? 1)
          ) / 10
        "
        style="width: 100%"
      />
    </div>
  </section>
</template>
