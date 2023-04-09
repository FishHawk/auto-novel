<script lang="ts" setup>
import { h, onMounted, Ref, ref } from 'vue';
import { NA, DataTableColumns, NButton, useMessage } from 'naive-ui';

import { ResultState } from '../data/api/result';
import ApiWebNovel, { BookStateDto } from '../data/api/api_web_novel';

import { update } from '../data/api/api_update';

const props = defineProps<{
  providerId: string;
  bookId: string;
  glossary: { [key: string]: string };
}>();

interface UpdateProgress {
  name: string;
  total?: number;
  finished: number;
  error: number;
}

const showModal = ref(false);

const message = useMessage();

const bookState = ref<ResultState<BookStateDto>>();
const progress: Ref<UpdateProgress | undefined> = ref();

onMounted(() => getFileGroups());
let lastPoll = false;
async function getFileGroups() {
  const result = await ApiWebNovel.getState(props.providerId, props.bookId);

  if (result.ok) {
    bookState.value = result;
  }

  if (progress.value || lastPoll) {
    lastPoll = progress.value !== undefined;
    window.setTimeout(() => getFileGroups(), 2000);
  }
}

async function startUpdateTask(
  needTranslate: boolean,
  startIndex: number,
  endIndex: number
) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  const name = needTranslate ? '更新中文' : '更新日文';
  progress.value = {
    name,
    finished: 0,
    error: 0,
  };

  const result = await update(
    needTranslate,
    props.providerId,
    props.bookId,
    startIndex,
    endIndex,
    {
      onStart: (total: number) => {
        progress.value!.total = total;
        getFileGroups();
      },
      onEpisodeTranslateSuccess: () => (progress.value!.finished += 1),
      onEpisodeTranslateFailure: () => (progress.value!.error += 1),
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

interface BookFiles {
  label: string;
  lang: string;
  files: { label: string; url: string; name: string }[];
}

const tableColumns: DataTableColumns<BookFiles> = [
  { title: '语言', key: 'label' },
  {
    title: '链接',
    key: 'links',
    render(row) {
      return row.files.map((file) => {
        return h(
          NA,
          {
            style: { marginRight: '6px' },
            href: file.url,
            target: '_blank',
            download: file.name,
          },
          { default: () => file.label }
        );
      });
    },
  },
  {
    title: '操作',
    key: 'actions',
    render(row) {
      if (row.lang === 'jp') {
        return h(
          NButton,
          {
            tertiary: true,
            size: 'small',
            onClick: () => startUpdateTask(false, 0, 65536),
          },
          { default: () => '更新' }
        );
      } else {
        return h(
          NButton,
          {
            tertiary: true,
            size: 'small',
            onClick: () => startUpdateTask(true, 0, 65536),
          },
          { default: () => '更新(需要插件)' }
        );
      }
    },
  },
];

function stateToFileList(): BookFiles[] {
  const baseUrl = window.origin + `/api/prepare-book/`;

  function createFile(label: string, lang: string, type: string) {
    return {
      label,
      url: baseUrl + `${props.providerId}/${props.bookId}/${lang}/${type}`,
      name: `${props.providerId}.${props.bookId}.${lang}.${type}`,
    };
  }

  let state: BookStateDto | undefined;
  if (bookState.value?.ok) {
    state = bookState.value.value;
  } else {
    state = undefined;
  }

  return [
    {
      label: `日文(${state?.countJp ?? '-'}/${state?.total ?? '-'})`,
      lang: 'jp',
      files: [createFile('TXT', 'jp', 'txt'), createFile('EPUB', 'jp', 'epub')],
    },
    {
      label: `中文(${state?.countZh ?? '-'}/${state?.total ?? '-'})`,
      lang: 'zh',
      files: [
        createFile('TXT', 'zh', 'txt'),
        createFile('EPUB', 'zh', 'epub'),
        createFile('中日对比TXT', 'mix', 'txt'),
        createFile('中日对比EPUB', 'mix', 'epub'),
      ],
    },
  ];
}

const formStartIndex = ref(1);
const formEndIndex = ref(65536);
enum FormMode {
  JP,
  ZH,
}
const formMode = ref(FormMode.JP);
const formModeOptions = [
  { label: '日文', value: FormMode.JP },
  { label: '中文', value: FormMode.ZH },
];

function submitForm() {
  if (formMode.value == FormMode.JP) {
    startUpdateTask(false, formStartIndex.value - 1, formEndIndex.value - 1);
  } else {
    startUpdateTask(true, formStartIndex.value - 1, formEndIndex.value - 1);
  }
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
  <n-data-table
    :columns="tableColumns"
    :data="stateToFileList()"
    :pagination="false"
    :bordered="false"
  />
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
