<script lang="ts" setup>
import { h, onMounted, Ref, ref } from 'vue';
import { NA, DataTableColumns, NButton, useMessage } from 'naive-ui';

import { ResultState } from '../data/api/result';
import ApiNovel, { BookStateDto, BookFiles } from '../data/api/api_novel';

import { UpdateProgress } from '../data/api/progress';
import { update } from '../data/api/api_update';
import { errorToString } from '../data/handle_error';

const props = defineProps<{
  providerId: string;
  bookId: string;
  showModal: boolean;
}>();
const emits = defineEmits(['update:showModal']);

const message = useMessage();

const bookState = ref<ResultState<BookStateDto>>();
const progress: Ref<UpdateProgress | undefined> = ref();

onMounted(() => getFileGroups());
let lastPoll = false;
async function getFileGroups() {
  const result = await ApiNovel.getState(props.providerId, props.bookId);

  if (result.ok) {
    bookState.value = result;
  }

  if (progress.value || lastPoll) {
    lastPoll = progress.value !== undefined;
    window.setTimeout(() => getFileGroups(), 2000);
  }
}

async function startUpdateJpTask(startIndex: number, endIndex: number) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  const result = await update(
    false,
    progress,
    props.providerId,
    props.bookId,
    startIndex,
    endIndex,
    () => getFileGroups()
  );
  progress.value = undefined;
  if (result.ok) {
    const progressHint = `${result.value.finished}/${result.value.total}`;
    message.success(`日文更新任务完成[${progressHint}]`);
  } else {
    console.log(result.error);
    message.error(`日文更新任务失败:${errorToString(result.error)}`);
  }
}

async function startUpdateZhTask(startIndex: number, endIndex: number) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  const result = await update(
    true,
    progress,
    props.providerId,
    props.bookId,
    startIndex,
    endIndex,
    () => getFileGroups()
  );
  progress.value = undefined;
  if (result.ok) {
    const progressHint = `${result.value.finished}/${result.value.total}`;
    message.success(`中文更新任务完成[${progressHint}]`);
  } else {
    console.log(result.error);
    message.error(`中文更新任务失败:${errorToString(result.error)}`);
  }
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
            onClick: () => startUpdateJpTask(0, 65536),
          },
          { default: () => '更新' }
        );
      } else {
        return h(
          NButton,
          {
            tertiary: true,
            size: 'small',
            onClick: () => startUpdateZhTask(0, 65536),
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
    startUpdateJpTask(formStartIndex.value - 1, formEndIndex.value - 1);
  } else {
    startUpdateZhTask(formStartIndex.value - 1, formEndIndex.value - 1);
  }
  emits('update:showModal', false);
}
</script>

<template>
  <n-modal
    :show="showModal"
    @update:show="(value:boolean) => emits('update:showModal', value)"
  >
    <n-card
      style="width: 600px"
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
        <n-button @click="emits('update:showModal', false)">取消</n-button>
      </n-space>
    </n-card>
  </n-modal>

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
