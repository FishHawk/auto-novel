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
  NTooltip,
} from 'naive-ui';
import { SearchOutlined, FormatListBulletedOutlined } from '@vicons/material';

import { handleError, Result, errorToString } from '../models/util';
import { addHistory } from '../models/history';
import { getContentMetadata, ContentMetadata } from '../models/book_content';
import {
  BookFileGroup,
  filenameToUrl,
  getFileTypes,
  getStorage,
} from '../models/book_storage';
import { buildMetadataUrl } from '../models/provider';

import { UpdateProgress } from '../update/progress';
import { runLocalBoost } from '../update/local';
import { runUpdate } from '../update/remote';

const route = useRoute();
const message = useMessage();
const showModal = ref(false);
const contentMetadata: Ref<Result<ContentMetadata, any> | undefined> = ref();
const fileGroups: Ref<Result<BookFileGroup[], any> | undefined> = ref();
const progress: Ref<UpdateProgress | undefined> = ref();

const formStartIndex = ref(1);
const formEndIndex = ref(65536);
enum FormMode {
  NORMAL,
  BOOST,
}
const formMode = ref(FormMode.NORMAL);
const formModeOptions = [
  { label: '常规更新', value: FormMode.NORMAL },
  { label: '本地加速', value: FormMode.BOOST },
];

function submitForm() {
  if (formMode.value == FormMode.NORMAL) {
    update(formStartIndex.value - 1, formEndIndex.value - 1, true);
  } else {
    localBoost(formStartIndex.value - 1, formEndIndex.value - 1);
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
  const metadata = await getContentMetadata(providerId, bookId);
  contentMetadata.value = metadata;
  if (metadata.ok) {
    addHistory({ url, title: metadata.value.title });
  }
}

let lastPoll = false;
async function getFileGroups() {
  const groups = await getStorage(providerId, bookId);

  if (groups.ok) {
    fileGroups.value = groups;
  }

  if (progress.value || lastPoll) {
    lastPoll = progress.value !== undefined;
    window.setTimeout(() => getFileGroups(), 2000);
  }
}

async function update(
  startIndex: number,
  endIndex: number,
  translated: boolean
) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  try {
    await runUpdate(
      providerId,
      bookId,
      startIndex,
      endIndex,
      translated,
      (it: UpdateProgress) => (progress.value = it),
      () => getFileGroups(),
      (it: UpdateProgress) => {
        message.success(`更新任务完成[${it.finished}/${it.total}]`);
        progress.value = undefined;
      }
    );
  } catch (error) {
    progress.value = undefined;
    console.log(error);
    handleError(message, error, '本地加速任务失败');
  }
}

async function localBoost(startIndex: number, endIndex: number) {
  if (progress.value !== undefined) {
    message.info('已有任务在运行。');
    return;
  }
  try {
    runLocalBoost(
      providerId,
      bookId,
      startIndex,
      endIndex,
      (it: UpdateProgress) => (progress.value = it),
      () => getFileGroups(),
      (it: UpdateProgress) => {
        message.success(`本地加速任务完成[${it.finished}/${it.total}]`);
        progress.value = undefined;
      }
    );
  } catch (error) {
    progress.value = undefined;
    console.log(error);
    handleError(message, error, '本地加速任务失败');
  }
}

const tableColumns: DataTableColumns<BookFileGroup> = [
  { title: '语言', key: 'status' },
  {
    title: '链接',
    key: 'links',
    render(row) {
      return getFileTypes(row.lang).map((file) => {
        return h(
          NA,
          {
            style: { marginRight: '6px' },
            href: filenameToUrl(providerId, bookId, row.lang, file.extension),
            target: '_blank',
          },
          { default: () => file.name }
        );
      });
    },
  },
  {
    title: '操作',
    key: 'actions',
    render(row) {
      const updateButton = h(
        NButton,
        {
          tertiary: true,
          size: 'small',
          onClick: () => update(0, 65536, row.lang === 'zh'),
        },
        { default: () => '更新' }
      );
      if (row.lang === 'jp') {
        return updateButton;
      } else {
        const newUpdateButton = h(NTooltip, null, {
          trigger: () => updateButton,
          default: () => '翻译api额度很紧张，大概率失败，推荐使用本地加速。',
        });
        const localBoostButton = h(
          NButton,
          {
            style: { marginLeft: '6px' },
            tertiary: true,
            size: 'small',
            onClick: () => localBoost(0, 65536),
          },
          { default: () => '本地加速' }
        );
        return [newUpdateButton, localBoostButton];
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
        <span>更新方式</span>
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
    <div v-if="contentMetadata?.ok">
      <n-h2 style="text-align: center; width: 100%">
        <n-a :href="url" target="_blank">{{ contentMetadata.value.title }}</n-a>
        <br />
        <span style="color: grey">{{ contentMetadata.value.zh_title }}</span>
      </n-h2>

      <n-space justify="space-around">
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
      </n-space>

      <div v-if="contentMetadata.value.authors.length > 0">
        作者：
        <span v-for="author in contentMetadata.value.authors">
          <n-a :href="author.link" target="_blank">{{ author.name }}</n-a>
        </span>
      </div>

      <n-p>{{ contentMetadata.value.introduction }}</n-p>
      <n-p v-if="contentMetadata.value.zh_introduction !== undefined">{{
        contentMetadata.value.zh_introduction
      }}</n-p>
    </div>

    <div v-if="fileGroups?.ok">
      <n-h2 prefix="bar" align-text>状态</n-h2>
      <n-p
        >如果需要自定义更新范围，请使用
        <n-a @click="showModal = true"> 高级模式 </n-a>。
      </n-p>
      <n-data-table
        :columns="tableColumns"
        :data="fileGroups.value"
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

    <div v-if="contentMetadata?.ok">
      <n-h2 prefix="bar" align-text>目录</n-h2>
      <n-ul>
        <n-li v-for="token in contentMetadata.value.toc">
          <span v-if="'level' in token" class="episode-base">
            <span class="episode-title">
              {{ token.title }}
            </span>
            <span class="episode-title" style="color: grey">
              {{ token.zh_title }}
            </span>
          </span>

          <n-a
            v-if="'episode_id' in token"
            class="episode-base"
            :href="`/novel/${providerId}/${bookId}/${token.episode_id}`"
          >
            <span class="episode-title">
              {{ token.title.trim().length > 0 ? token.title : '短篇' }}
            </span>
            <span class="episode-title" style="color: grey">
              {{ token.zh_title }}
            </span>
          </n-a>

          <n-divider style="margin-top: 2px; margin-bottom: 2px" />
        </n-li>
      </n-ul>
    </div>
  </div>

  <div v-if="contentMetadata && !contentMetadata.ok">
    <n-result
      status="error"
      title="加载错误"
      :description="errorToString(contentMetadata.error)"
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
