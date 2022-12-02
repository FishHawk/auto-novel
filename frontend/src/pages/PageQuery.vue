<script lang="ts" setup>
import { onMounted, Ref, ref } from 'vue';
import { ElMessage } from 'element-plus';
import 'element-plus/es/components/message/style/css';
import ky from 'ky';

import SearchBar from '../components/SearchBar.vue';
import { Book, SearchHistory, LocalBoostProgress } from '../models/Book';
import { handleError } from '../models/Util';
import { BaiduWebTranslator } from '../translator/baidu-web';
import {
  get_metadata,
  post_metadata,
  get_episode,
  post_episode,
  make_book,
} from '../translator/base';

const bookRef: Ref<Book | undefined> = ref();
const localBoostProgressRef: Ref<LocalBoostProgress | undefined> = ref();
const loadingRef = ref(false);
let query_id = 0;
let poll_id: number | undefined = undefined;

const historiesRef: Ref<SearchHistory[]> = ref([]);

onMounted(() => {
  const histories_raw = localStorage.getItem('histories');
  if (histories_raw) {
    try {
      historiesRef.value = JSON.parse(histories_raw);
    } catch (e) {
      localStorage.removeItem('histories');
    }
  }
});

function addHistory(history: SearchHistory) {
  const histories = historiesRef.value.filter(
    (item) => item.url != history.url
  );
  histories.unshift(history);

  const histories_length_limit = 10;
  if (histories.length > histories_length_limit)
    histories.length = histories_length_limit;

  historiesRef.value = histories;
  const parsed = JSON.stringify(historiesRef.value);
  localStorage.setItem('histories', parsed);
}

function onSearch(url: string) {
  if (loadingRef.value) return;

  bookRef.value = undefined;
  localBoostProgressRef.value = undefined;
  loadingRef.value = true;
  query_id += 1;
  clearTimeout(poll_id);

  const query_id_snapshot = query_id;
  ky.get('/api/query', { searchParams: { url } })
    .json<Book>()
    .then((book) => {
      if (query_id_snapshot == query_id) {
        bookRef.value = book;
        loadingRef.value = false;
        addHistory({ url: book.url, title: book.title });
        pollSearchIfNeed(book, url, query_id_snapshot);
      }
    })
    .catch((error) => {
      if (query_id_snapshot == query_id) {
        loadingRef.value = false;
        handleError(error, '查询失败');
      }
    });
}

function pollSearchIfNeed(book: Book, url: string, query_id_snapshot: number) {
  const hasActivitedJob = book.files.some((item) => {
    return (
      item.status == 'queued' ||
      item.status == 'started' ||
      localBoostProgressRef.value !== undefined
    );
  });
  if (hasActivitedJob) {
    poll_id = window.setTimeout(() => {
      doPollSearch(url, query_id_snapshot);
    }, 2000);
  }
}

function doPollSearch(url: string, query_id_snapshot: number) {
  ky.get('/api/query', { searchParams: { url } })
    .json<Book>()
    .then((book) => {
      if (query_id_snapshot == query_id) {
        bookRef.value = book;
        pollSearchIfNeed(book, url, query_id_snapshot);
      }
    })
    .catch(() => {
      if (query_id_snapshot == query_id) {
        poll_id = window.setTimeout(() => {
          doPollSearch(url, query_id_snapshot);
        }, 2000);
      }
    });
}

function normalUpdate(lang: string, start_index: number, end_index: number) {
  const query_id_snapshot = query_id;
  if (bookRef.value === undefined) return;
  const provider_id = bookRef.value.provider_id;
  const book_id = bookRef.value.book_id;
  ky.post(`/api/book-update/${provider_id}/${book_id}/${lang}`, {
    searchParams: {
      start_index,
      end_index,
    },
  })
    .text()
    .then((_) => {
      ElMessage.success(`更新任务已经进入队列。`);
      if (query_id === query_id_snapshot && bookRef.value) {
        doPollSearch(bookRef.value.url, query_id);
      }
    })
    .catch((error) => {
      handleError(error, '更新失败');
    });
}

async function localBoost(start_index: number, end_index: number) {
  const query_id_snapshot = query_id;
  if (bookRef.value === undefined) return;
  const provider_id = bookRef.value.provider_id;
  const book_id = bookRef.value.book_id;

  if (localBoostProgressRef.value !== undefined) {
    ElMessage.info('本地加速已经在运行。');
    return;
  }

  const progress: LocalBoostProgress = {
    total: undefined,
    error: 0,
    finished: 0,
  };
  localBoostProgressRef.value = progress;

  try {
    const translator = await BaiduWebTranslator.createInstance('jp', 'zh');

    console.log(`获取元数据 ${provider_id}/${book_id}`);
    const metadata = await get_metadata(
      provider_id,
      book_id,
      start_index,
      end_index
    );

    console.log(`翻译元数据 ${provider_id}/${book_id}`);
    const translated_metadata = await translator.translate(metadata.metadata);

    console.log(`上传元数据 ${provider_id}/${book_id}`);
    await post_metadata(provider_id, book_id, translated_metadata);

    if (query_id === query_id_snapshot) {
      progress.total = metadata.episode_ids.length;
      localBoostProgressRef.value = {
        total: progress.total,
        error: progress.error,
        finished: progress.finished,
      };
      doPollSearch(bookRef.value.url, query_id);
    } else {
      return;
    }

    for (const episode_id of metadata.episode_ids) {
      try {
        console.log(`获取章节 ${provider_id}/${book_id}/${episode_id}`);
        const episode = await get_episode(provider_id, book_id, episode_id);

        console.log(`翻译章节 ${provider_id}/${book_id}/${episode_id}`);
        const translated_episode = await translator.translate(episode);

        console.log(`上传章节 ${provider_id}/${book_id}/${episode_id}`);
        await post_episode(
          provider_id,
          book_id,
          episode_id,
          translated_episode
        );
        if (query_id === query_id_snapshot) {
          progress.finished += 1;
          localBoostProgressRef.value = {
            total: progress.total,
            error: progress.error,
            finished: progress.finished,
          };
        } else {
          return;
        }
      } catch {
        if (query_id === query_id_snapshot) {
          progress.error += 1;
          localBoostProgressRef.value = {
            total: progress.total,
            error: progress.error,
            finished: progress.finished,
          };
        } else {
          return;
        }
      }
    }

    console.log(`制作 ${provider_id}/${book_id}`);
    await make_book(provider_id, book_id);

    if (query_id === query_id_snapshot) {
      localBoostProgressRef.value = undefined;
      ElMessage.success(
        `本地加速任务完成[${progress.finished}/${progress.total}]: ${provider_id}/${book_id}`
      );
    } else {
      return;
    }
  } catch (error) {
    if (query_id === query_id_snapshot) {
      localBoostProgressRef.value = undefined;
      console.log(error);
      handleError(error, '本地加速任务失败');
    }
  }
}

const dialogVisible = ref(false);
function openDescriptionDialog() {
  dialogVisible.value = true;
}
</script>

<template>
  <el-dialog
    v-model="dialogVisible"
    title="本地加速使用说明"
    style="max-width: 700px"
  >
    <p>
      本地加速是在你本地调用翻译api，从而解决翻译额度的问题。
      一般来说浏览器是无法跨域访问的，所以我们需要一些特殊的配置。
    </p>
    <p>
      对于Chrome/Edge/Firefox浏览器，你需要安装插件 CORS Unblock。下载链接：
      <a
        href="https://chrome.google.com/webstore/detail/cors-unblock/lfhmikememgdcahcdlaciloancbhjino/"
        target="_blank"
      >
        Chrome
      </a>
      /
      <a
        href="https://microsoftedge.microsoft.com/addons/detail/cors-unblock/hkjklmhkbkdhlgnnfbbcihcajofmjgbh"
        target="_blank"
      >
        Edge
      </a>
      /
      <a
        href="https://addons.mozilla.org/en-US/firefox/addon/cors-unblock/"
        target="_blank"
      >
        Firefox
      </a>
      。如果无法访问Chrome的插件页面，你也可以从
      <a href="/CORS-Unblock.crx" target="_blank">这里</a>
      直接下载插件文件。
      为了安全，建议只在本站点启用该插件。安装以后，具体配置如下图所示。
    </p>
    <img
      src="/extension_options.png"
      alt="extension options"
      style="max-width: 100%"
    />
    <!-- <p>对于Safari浏览器，你可以使用命令xxx启动，来关闭跨域检查。</p> -->
  </el-dialog>
  <el-col style="margin-top: 15%; margin-bottom: 15%">
    <h1>网络小说 EPUB/TXT 生成器</h1>

    <el-row justify="center">
      <SearchBar
        @onSearch="onSearch"
        :loading="loadingRef"
        :histories="historiesRef"
      />
    </el-row>

    <ul>
      <li class="support-text">
        链接示例(KAKUYOMU): https://kakuyomu.jp/works/16817139555217983105
      </li>
      <li class="support-text">
        链接示例(成为小说家吧): https://ncode.syosetu.com/n0833hi
      </li>
      <li class="support-text">
        链接示例(ノベルアップ＋): https://novelup.plus/story/206612087/479535927
      </li>
      <li class="support-text">
        链接示例(HAMELN): https://syosetu.org/novel/297874/
      </li>
      <li class="support-text">
        链接示例(Pixiv): https://www.pixiv.net/novel/series/9406879
      </li>
      <li class="support-text">
        因为翻译额度有限，强烈建议使用本地加速，需要安装浏览器插件，见
        <span @click="openDescriptionDialog" style="text-decoration: underline">
          使用说明
        </span>
        。
      </li>
      <li class="support-text">
        如果你需要控制更新的章节范围，请点击高级按钮。
      </li>
      <li class="support-text">
        你也可以直接浏览缓存的
        <a href="#/list" target="_blank">文件列表</a>
        。
      </li>
    </ul>

    <el-row justify="center" style="margin-top: 30px">
      <el-col :span="24">
        <BookCard
          v-if="bookRef !== undefined"
          :book="bookRef"
          :local-boost-progress="localBoostProgressRef"
          @onNormalUpdate="normalUpdate"
          @onLocalBoost="localBoost"
        />
      </el-col>
    </el-row>

    <el-link
      href="https://github.com/FishHawk/web-novel-ebook-generator"
      target="_blank"
      class="footer"
    >
      @GitHub
    </el-link>
  </el-col>
</template>

<style scoped>
.support-text {
  color: #b4bcc2;
  font-size: 18px;
  margin-top: 5px;
  margin-bottom: 5px;
}

.support-text a {
  color: #b4bcc2;
}

.footer {
  position: fixed;
  left: 50%;
  bottom: 20px;
  transform: translate(-50%, -50%);
  margin: 0 auto;
  color: #b4bcc2;
}
</style>
