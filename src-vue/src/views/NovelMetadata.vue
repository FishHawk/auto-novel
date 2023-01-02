<script lang="ts" setup>
import { h, onMounted, Ref, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  NA,
  DataTableColumns,
  NButton,
  NDataTable,
  useMessage,
  NP,
} from 'naive-ui';
import { SearchOutlined, FormatListBulletedOutlined } from '@vicons/material';

import { ResultRef } from '../api/result';
import ApiNovel, {
  BookMetadataDto,
  BookStateDto,
  BookFiles,
  stateToFileList,
} from '../api/api_novel';
import { addHistory } from '../data/history';
import { buildMetadataUrl } from '../data/provider';

import { UpdateProgress } from '../api/progress';
import { updateJp } from '../api/api_update_jp';
import { updateZh } from '../api/api_update_zh';
import { errorToString } from '../data/handle_error';

const route = useRoute();
const message = useMessage();
const showModal = ref(false);
const bookMetadata: ResultRef<BookMetadataDto> = ref();
const bookState: ResultRef<BookStateDto> = ref();
const progress: Ref<UpdateProgress | undefined> = ref();

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
  showModal.value = false;
}

onMounted(() => {
  getMetadata();
  getFileGroups();
});

const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const url = buildMetadataUrl(providerId, bookId);

async function getMetadata() {
  const result = await ApiNovel.getMetadata(providerId, bookId);
  bookMetadata.value = result;
  if (result.ok) {
    addHistory({ url, title: result.value.titleJp });
  }
}

let lastPoll = false;
async function getFileGroups() {
  const result = await ApiNovel.getState(providerId, bookId);

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
  const result = await updateJp(
    progress,
    providerId,
    bookId,
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
  const result = await updateZh(
    progress,
    providerId,
    bookId,
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
</script>

<template>
  <n-modal v-model:show="showModal">
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
        <n-button @click="showModal = false">取消</n-button>
      </n-space>
    </n-card>
  </n-modal>

  <div class="content">
    <div v-if="bookMetadata?.ok">
      <n-h2 style="text-align: center; width: 100%">
        <n-a :href="url" target="_blank">{{ bookMetadata.value.titleJp }}</n-a>
        <br />
        <span style="color: grey">{{ bookMetadata.value.titleZh }}</span>
      </n-h2>

      <n-space align="center" justify="space-around">
        <n-a href="/">
          <n-button text>
            <template #icon>
              <n-icon> <SearchOutlined /> </n-icon>
            </template>
            搜索
          </n-button>
        </n-a>
        <n-a href="/list">
          <n-button text>
            <template #icon>
              <n-icon> <FormatListBulletedOutlined /> </n-icon>
            </template>
            列表
          </n-button>
        </n-a>
        <span>浏览次数:{{ bookMetadata.value.visited }}</span>
        <span>下载次数:{{ bookMetadata.value.downloaded }}</span>
      </n-space>

      <div v-if="bookMetadata.value.authors.length > 0">
        作者：
        <span v-for="author in bookMetadata.value.authors">
          <n-a :href="author.link" target="_blank">{{ author.name }}</n-a>
        </span>
      </div>

      <n-p>{{ bookMetadata.value.introductionJp }}</n-p>
      <n-p v-if="bookMetadata.value.introductionZh !== undefined">{{
        bookMetadata.value.introductionZh
      }}</n-p>
    </div>

    <div v-if="bookState?.ok">
      <n-h2 prefix="bar" align-text>状态</n-h2>
      <n-p
        >如果需要自定义更新范围，请使用
        <n-a @click="showModal = true"> 高级模式 </n-a>。
      </n-p>
      <n-data-table
        :columns="tableColumns"
        :data="stateToFileList(providerId, bookId, bookState.value)"
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
            (100 * (progress.finished + progress.error)) / (progress.total ?? 1)
          "
          style="width: 100%"
        />
      </div>
    </div>

    <div v-if="bookMetadata?.ok">
      <n-h2 prefix="bar" align-text>目录</n-h2>
      <n-ul>
        <n-li v-for="token in bookMetadata.value.toc">
          <span v-if="!token.episodeId" class="episode-base">
            <span class="episode-title">
              {{ token.titleJp }}
            </span>
            <span class="episode-title" style="color: grey">
              {{ token.titleZh }}
            </span>
          </span>

          <n-a
            v-if="token.episodeId"
            class="episode-base"
            :href="`/novel/${providerId}/${bookId}/${token.episodeId}`"
          >
            <span class="episode-title">
              {{ token.titleJp }}
            </span>
            <span class="episode-title" style="color: grey">
              {{ token.titleZh }}
            </span>
          </n-a>

          <n-divider style="margin-top: 2px; margin-bottom: 2px" />
        </n-li>
      </n-ul>
    </div>
  </div>

  <div v-if="bookMetadata && !bookMetadata.ok">
    <n-result
      status="error"
      title="加载错误"
      :description="errorToString(bookMetadata.error)"
    />
  </div>
</template>

<style scoped>
.episode-base {
  width: 100%;
  position: relative;
  display: block;
}
.episode-title {
  display: inline-block;
  word-wrap: break-word;
  width: 50%;
}
</style>
