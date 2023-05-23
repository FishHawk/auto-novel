<script lang="ts" setup>
import { computed, onMounted, Ref, ref } from 'vue';
import { useMessage } from 'naive-ui';
import { useWindowSize } from '@vueuse/core';

import { ResultState } from '@/data/api/result';
import ApiWebNovel, { WebNovelStateDto } from '@/data/api/api_web_novel';
import { update } from '@/data/api/api_update';

const props = defineProps<{
  providerId: string;
  novelId: string;
  glossary: { [key: string]: string };
}>();

interface UpdateProgress {
  name: string;
  total?: number;
  finished: number;
  error: number;
}

const { width } = useWindowSize();
const isDesktop = computed(() => width.value > 600);

const showModal = ref(false);

const message = useMessage();

const novelState = ref<ResultState<WebNovelStateDto>>();
const progress: Ref<UpdateProgress | undefined> = ref();

onMounted(() => getFileGroups());
let lastPoll = false;
async function getFileGroups() {
  const result = await ApiWebNovel.getState(props.providerId, props.novelId);

  if (result.ok) {
    novelState.value = result;
  }

  if (progress.value || lastPoll) {
    lastPoll = progress.value !== undefined;
    window.setTimeout(() => getFileGroups(), 2000);
  }
}

async function startUpdateTask(
  version: 'jp' | 'baidu' | 'youdao',
  startIndex: number,
  endIndex: number
) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  let name;
  if (version === 'jp') {
    name = '更新日文';
  } else if (version === 'baidu') {
    name = '百度翻译';
  } else {
    name = '有道翻译';
  }

  progress.value = {
    name,
    finished: 0,
    error: 0,
  };

  const result = await update(
    version,
    props.providerId,
    props.novelId,
    startIndex,
    endIndex,
    {
      onStart: (total: number) => {
        progress.value!.total = total;
        getFileGroups();
      },
      onChapterTranslateSuccess: () => (progress.value!.finished += 1),
      onChapterTranslateFailure: () => (progress.value!.error += 1),
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
  version: 'jp' | 'baidu' | 'youdao';
  files: { label: string; url: string; name: string }[];
}

function stateToFileList(): NovelFiles[] {
  const baseUrl = window.origin + `/api/prepare-book/`;

  function createFile(label: string, lang: string, type: string) {
    return {
      label,
      url: baseUrl + `${props.providerId}/${props.novelId}/${lang}/${type}`,
      name: `${props.providerId}.${props.novelId}.${lang}.${type}`,
    };
  }

  let state: WebNovelStateDto | undefined;
  if (novelState.value?.ok) {
    state = novelState.value.value;
  } else {
    state = undefined;
  }

  return [
    {
      label: `日文(${state?.count ?? '-'}/${state?.total ?? '-'})`,
      version: 'jp',
      files: [createFile('TXT', 'jp', 'txt'), createFile('EPUB', 'jp', 'epub')],
    },
    {
      label: `百度(${state?.countBaidu ?? '-'}/${state?.total ?? '-'})`,
      version: 'baidu',
      files: [
        createFile('TXT', 'zh-baidu', 'txt'),
        createFile('中日对比TXT', 'mix-baidu', 'txt'),
        createFile('EPUB', 'zh-baidu', 'epub'),
        createFile('中日对比EPUB', 'mix-baidu', 'epub'),
      ],
    },
    {
      label: `有道(${state?.countYoudao ?? '-'}/${state?.total ?? '-'})`,
      version: 'youdao',
      files: [
        createFile('TXT', 'zh-youdao', 'txt'),
        createFile('中日对比TXT', 'mix-youdao', 'txt'),
        createFile('EPUB', 'zh-youdao', 'epub'),
        createFile('中日对比EPUB', 'mix-youdao', 'epub'),
      ],
    },
  ];
}

const formStartIndex = ref(1);
const formEndIndex = ref(65536);
const formMode = ref<'jp' | 'baidu' | 'youdao'>('jp');
const formModeOptions = [
  { label: '日文', value: 'jp' },
  { label: '百度', value: 'baidu' },
  { label: '有道', value: 'youdao' },
];

function submitForm() {
  startUpdateTask(
    formMode.value,
    formStartIndex.value - 1,
    formEndIndex.value - 1
  );
  showModal.value = false;
}
</script>

<template>
  <n-modal v-model:show="showModal">
    <n-card
      style="width: min(500px, calc(100% - 16px))"
      title="高级"
      :bordered="false"
      size="huge"
      role="dialog"
      aria-modal="true"
    >
      <n-space>
        <span>从这章开始更新</span>
        <n-input-number v-model:value="formStartIndex" :min="1" clearable />
      </n-space>

      <n-space style="margin-top: 15px">
        <span>到这章为止</span>
        <n-input-number v-model:value="formEndIndex" :min="1" clearable />
      </n-space>

      <n-space style="margin-top: 15px">
        <span>语言</span>
        <n-radio-group v-model:value="formMode" name="update-mode">
          <n-space>
            <n-radio
              v-for="option in formModeOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </n-radio>
          </n-space>
        </n-radio-group>
      </n-space>
      <n-space style="margin-top: 15px">
        <n-button @click="submitForm()">更新</n-button>
        <n-button @click="showModal = false">取消</n-button>
      </n-space>
    </n-card>
  </n-modal>

  <n-h2 prefix="bar">翻译</n-h2>
  <n-p>
    网页端翻译需要安装插件，请查看
    <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
    移动端暂时无法翻译。
  </n-p>
  <n-p>
    如果需要自定义更新范围，请使用
    <n-a @click="showModal = true">高级模式</n-a>
    。如果要编辑术语表，请先进入编辑界面。
  </n-p>
  <n-p v-if="Object.keys(glossary).length">
    <n-collapse>
      <n-collapse-item title="术语表">
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
            tertiary
            size="small"
            @click="startUpdateTask(row.version, 0, 65536)"
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
          tertiary
          size="small"
          @click="startUpdateTask(row.version, 0, 65536)"
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
          (1000 * (progress.finished + progress.error)) / (progress.total ?? 1)
        ) / 10
      "
      style="width: 100%"
    />
  </div>
</template>
