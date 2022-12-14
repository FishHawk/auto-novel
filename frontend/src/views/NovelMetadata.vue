<script lang="ts" setup>
import { h, onMounted, Ref, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  NA,
  DataTableColumns,
  NButton,
  NDataTable,
  useMessage,
  NP,
} from 'naive-ui';
import { SearchOutlined, FormatListBulletedOutlined } from '@vicons/material';

import { handleError, Result } from '../models/util';
import { addHistory, LocalBoostProgress } from '../models/history';
import {
  getBoostEpisode,
  getBoostMetadata,
  postBoostEpisode,
  postBoostMakeBook,
  postBoostMetadata,
} from '../models/book_boost';
import {
  getContentMetadata,
  ContentMetadata,
  TocChapterToken,
  TocEpisodeToken,
} from '../models/book_content';
import {
  BookFileGroup,
  filenameToUrl,
  getStorage,
  postStorageTask,
} from '../models/book_storage';
import { BaiduWebTranslator } from '../translator/baidu-web';

const route = useRoute();
const router = useRouter();
const message = useMessage();
const showModal = ref(false);
const contentMetadata: Ref<Result<ContentMetadata, any> | undefined> = ref();
const fileGroups: Ref<Result<BookFileGroup[], any> | undefined> = ref();
const localBoostProgress: Ref<LocalBoostProgress | undefined> = ref();

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
    update('zh', formStartIndex.value - 1, formEndIndex.value - 1);
  } else {
    localBoost(formStartIndex.value - 1, formEndIndex.value - 1);
  }
  showModal.value = false;
}

onMounted(() => {
  getMetadata();
  getFileGroups();
});

async function getMetadata() {
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const metadata = await getContentMetadata(providerId, bookId);
  contentMetadata.value = metadata;
  if (metadata.ok) {
    addHistory({ url: metadata.value.url, title: metadata.value.title });
  }
}

let pollId = 0;
async function getFileGroups() {
  const pollIdSnapshot = pollId;
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const groups = await getStorage(providerId, bookId);

  if (groups.ok) {
    fileGroups.value = groups;

    const hasActivitedJob = groups.value.some((item) => {
      return (
        item.statusCode == 'queued' ||
        item.statusCode == 'started' ||
        localBoostProgress.value !== undefined
      );
    });
    if (!hasActivitedJob) return;
  }

  if (pollId === pollIdSnapshot) {
    pollId = window.setTimeout(() => {
      getFileGroups();
    }, 2000);
  }
}

function instanceOfTocEpisodeToken(
  object: TocChapterToken | TocEpisodeToken
): object is TocEpisodeToken {
  return 'episode_id' in object;
}

async function update(lang: string, startIndex: number, endIndex: number) {
  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const result = await postStorageTask(
    providerId,
    bookId,
    lang,
    startIndex,
    endIndex
  );
  if (result.ok) {
    message.success('更新任务已经进入队列。');
    pollId += 1;
    getFileGroups();
  } else {
    handleError(message, result.error, '更新失败');
  }
}

async function localBoost(startIndex: number, endIndex: number) {
  if (localBoostProgress.value !== undefined) {
    message.info('本地加速已经在运行。');
    return;
  }

  const providerId = route.params.providerId as string;
  const bookId = route.params.bookId as string;
  const progress: LocalBoostProgress = {
    total: undefined,
    error: 0,
    finished: 0,
  };
  localBoostProgress.value = progress;

  try {
    const translator = await BaiduWebTranslator.createInstance('jp', 'zh');

    console.log(`获取元数据 ${providerId}/${bookId}`);
    const metadata = await getBoostMetadata(
      providerId,
      bookId,
      startIndex,
      endIndex
    );

    console.log(`翻译元数据 ${providerId}/${bookId}`);
    const translated_metadata = await translator.translate(metadata.metadata);

    console.log(`上传元数据 ${providerId}/${bookId}`);
    await postBoostMetadata(providerId, bookId, translated_metadata);

    progress.total = metadata.episode_ids.length;
    localBoostProgress.value = {
      total: progress.total,
      error: progress.error,
      finished: progress.finished,
    };
    getFileGroups();

    for (const episodeId of metadata.episode_ids) {
      try {
        console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
        const episode = await getBoostEpisode(providerId, bookId, episodeId);

        console.log(`翻译章节 ${providerId}/${bookId}/${episodeId}`);
        const translated_episode = await translator.translate(episode);

        console.log(`上传章节 ${providerId}/${bookId}/${episodeId}`);
        await postBoostEpisode(
          providerId,
          bookId,
          episodeId,
          translated_episode
        );

        progress.finished += 1;
        localBoostProgress.value = {
          total: progress.total,
          error: progress.error,
          finished: progress.finished,
        };
      } catch {
        progress.error += 1;
        localBoostProgress.value = {
          total: progress.total,
          error: progress.error,
          finished: progress.finished,
        };
      }
    }

    console.log(`制作 ${providerId}/${bookId}`);
    await postBoostMakeBook(providerId, bookId);

    localBoostProgress.value = undefined;
    message.success(
      `本地加速任务完成[${progress.finished}/${progress.total}]: ${providerId}/${bookId}`
    );
  } catch (error) {
    localBoostProgress.value = undefined;
    console.log(error);
    handleError(message, error, '本地加速任务失败');
  }
}

const tableColumns: DataTableColumns<BookFileGroup> = [
  {
    title: '语言',
    key: 'lang',
  },
  {
    title: '状态',
    key: 'status',
  },
  {
    title: '链接',
    key: 'files',
    render(row) {
      return row.files.map((file) => {
        if (file.filename === null) {
          return h(
            NA,
            { style: { marginRight: '6px', color: 'grey' } },
            { default: () => file.type.toUpperCase() }
          );
        } else {
          return h(
            NA,
            {
              style: { marginRight: '6px' },
              href: filenameToUrl(file.filename),
              target: '_blank',
            },
            { default: () => file.type.toUpperCase() }
          );
        }
      });
    },
  },
  {
    title: '原文对比版链接',
    key: 'mixFiles',
    render(row) {
      return row.mixedFiles.map((file) => {
        if (file.filename === null) {
          return h(
            NA,
            { style: { marginRight: '6px', color: 'grey' } },
            { default: () => file.type.toUpperCase() }
          );
        } else {
          return h(
            NA,
            {
              style: { marginRight: '6px' },
              href: filenameToUrl(file.filename),
              target: '_blank',
            },
            { default: () => file.type.toUpperCase() }
          );
        }
      });
    },
  },
  {
    title: '操作',
    key: 'actions',
    render(row) {
      const arr = [
        h(
          NButton,
          {
            style: { marginRight: '6px' },
            tertiary: true,
            size: 'small',
            onClick: () => update(row.langCode, 0, 65536),
          },
          { default: () => '更新' }
        ),
      ];
      if (row.langCode === 'zh') {
        arr.push(
          h(
            NButton,
            {
              style: { marginRight: '6px' },
              tertiary: true,
              size: 'small',
              onClick: () => localBoost(0, 65536),
            },
            { default: () => '本地加速' }
          )
        );
      }
      return arr;
    },
  },
];

function getPercentage(progress: LocalBoostProgress): number {
  if (progress.total === undefined) {
    return 0;
  } else {
    return (100 * (progress.finished + progress.error)) / progress.total;
  }
}
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

  <div class="content" v-if="contentMetadata?.ok" style="margin-bottom: 40px">
    <!-- hacky, prevent margin collapse -->
    <div style="display: inline-block" />

    <n-h2 prefix="bar" align-text>
      <n-a :href="contentMetadata.value.url" target="_blank">
        {{ contentMetadata.value.title }}
      </n-a>
      <br />
      <n-text
        id="novel-title-secondary"
        v-if="contentMetadata.value.zh_title !== undefined"
      >
        {{ contentMetadata.value.zh_title }}
      </n-text>
    </n-h2>

    <div style="margin-bottom: 15px">
      <n-a href="/">
        <n-button text>
          <n-icon><SearchOutlined /></n-icon> 搜索
        </n-button>
      </n-a>
      <n-a href="/list" style="margin-start: 20px">
        <n-button text>
          <n-icon><FormatListBulletedOutlined /></n-icon> 列表
        </n-button>
      </n-a>
    </div>

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

    <n-h2 prefix="bar" align-text>状态</n-h2>
    <n-p
      >如果需要自定义更新范围，请使用
      <n-a @click="showModal = true"> 高级模式 </n-a>。
    </n-p>
    <n-data-table
      v-if="fileGroups !== undefined && fileGroups.ok"
      :columns="tableColumns"
      :data="fileGroups.value"
      :pagination="false"
      :bordered="false"
    />
    <div v-if="localBoostProgress !== undefined">
      <n-space
        v-if="localBoostProgress !== undefined"
        align="center"
        justify="space-between"
        style="width: 100%"
      >
        <span>本地加速</span>
        <div>
          <span>成功:{{ localBoostProgress.finished ?? '-' }}</span>
          <n-divider vertical />
          <span>失败:{{ localBoostProgress.error ?? '-' }}</span>
          <n-divider vertical />
          <span>总共:{{ localBoostProgress.total ?? '-' }}</span>
        </div>
      </n-space>
      <n-progress
        type="line"
        :percentage="getPercentage(localBoostProgress)"
        style="width: 100%"
      />
    </div>

    <n-h2 prefix="bar" align-text>目录</n-h2>
    <n-ul>
      <n-li v-for="token in contentMetadata.value.toc">
        <span v-if="!instanceOfTocEpisodeToken(token)" class="episode-base">
          <span class="episode-title">
            {{ token.title }}
          </span>
          <span class="episode-title-secondary">
            {{ token.zh_title }}
          </span>
        </span>

        <n-a
          v-if="instanceOfTocEpisodeToken(token)"
          class="episode-base"
          :href="`/novel/${route.params.providerId}/${route.params.bookId}/${token.episode_id}`"
        >
          <span class="episode-title">
            {{ token.title.trim().length > 0 ? token.title : '短篇' }}
          </span>
          <span class="episode-title-secondary">
            {{ token.zh_title }}
          </span>
        </n-a>

        <n-divider style="margin-top: 2px; margin-bottom: 2px" />
      </n-li>
    </n-ul>
  </div>
</template>

<style scoped>
#novel-title-secondary {
  color: gray;
}

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

.episode-title-secondary {
  display: inline-block;
  word-wrap: break-word;
  width: 50%;
  color: grey;
}
</style>
