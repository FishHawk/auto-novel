<script lang="ts" setup>
import { computed, onMounted, Ref, ref } from 'vue';
import { useRouter } from 'vue-router';
import { useMessage } from 'naive-ui';
import { useWindowSize } from '@vueuse/core';

import ApiUser from '@/data/api/api_user';
import { ApiWebNovel, WebNovelListItemDto } from '@/data/api/api_web_novel';
import { ApiWenkuNovel, WenkuListItemDto } from '@/data/api/api_wenku_novel';
import { parseUrl } from '@/data/provider';
import { Ok, ResultState } from '@/data/api/result';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const { width } = useWindowSize();
const isDesktop = computed(() => width.value > 600);

const router = useRouter();
const message = useMessage();

const url: Ref<string> = ref('');

function query(url: string) {
  if (url.length === 0) {
    return;
  }

  const parseResult = parseUrl(url);

  if (parseResult === undefined) {
    message.error('无法解析网址，可能是因为格式错误或者不支持');
    return;
  }

  const providerId = parseResult.providerId;
  const novelId = parseResult.novelId;
  const novelUrl = `/novel/${providerId}/${novelId}`;
  router.push({ path: novelUrl });
}

const authInfoStore = useAuthInfoStore();
const favoriteList = ref<ResultState<WebNovelListItemDto[]>>();
async function loadFavorite() {
  const result = await ApiUser.listFavoritedWebNovel(
    0,
    8,
    authInfoStore.token!!
  );
  if (result.ok) {
    favoriteList.value = Ok(result.value.items);
  } else {
    favoriteList.value = result;
  }
}
onMounted(loadFavorite);

const latestUpdateWeb = ref<ResultState<WebNovelListItemDto[]>>();
async function loadLatestUpdateWeb() {
  const result = await ApiWebNovel.list(0, 8, '', '');
  if (result.ok) {
    latestUpdateWeb.value = Ok(result.value.items);
  } else {
    latestUpdateWeb.value = result;
  }
}
onMounted(loadLatestUpdateWeb);

const latestUpdateWenku = ref<ResultState<WenkuListItemDto[]>>();
async function loadLatestUpdateWenku() {
  const result = await ApiWenkuNovel.list(0, '');
  if (result.ok) {
    latestUpdateWenku.value = Ok(result.value.items.slice(0, 12));
  } else {
    latestUpdateWenku.value = result;
  }
}
onMounted(loadLatestUpdateWenku);
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        style="
      background: url('https://images.unsplash.com/photo-1521587760476-6c12a4b040da?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80')
      width: 100%;
      padding-top: 60px;
      padding-bottom: 60px;
      margin-bottom: 20px;
    "
      >
        <div class="container" style="max-width: 800px">
          <n-h1
            style="
              text-align: center;
              font-size: 3.5em;
              color: white;
              filter: drop-shadow(0.05em 0.05em black);
            "
          >
            日本网文机翻机器人
          </n-h1>
          <n-input-group>
            <n-input
              v-model:value="url"
              size="large"
              placeholder="请输入小说链接..."
              @keyup.enter="query(url)"
            />
            <n-button size="large" type="primary" @click="query(url)">
              搜索
            </n-button>
          </n-input-group>
        </div>
      </div>
    </template>

    <template v-if="isDesktop">
      <div style="display: flex">
        <div style="flex: 0.7; margin-right: 20px">
          <PanelAnnouncement />
        </div>
        <div style="flex: 1">
          <PanelLinkExample />
        </div>
      </div>
      <n-divider />
    </template>

    <template v-else>
      <PanelAnnouncement />
      <n-divider />
      <PanelLinkExample />
      <n-divider />
    </template>

    <template v-if="authInfoStore.token">
      <n-space align="center" justify="space-between">
        <n-h2 prefix="bar">我的收藏</n-h2>
        <n-a href="/favorite-list">更多</n-a>
      </n-space>
      <PanelWebNovel :list="favoriteList" />
      <n-divider />
    </template>

    <n-space align="center" justify="space-between">
      <n-h2 prefix="bar">最新更新-网络小说</n-h2>
      <n-a href="/novel-list">更多</n-a>
    </n-space>
    <PanelWebNovel :list="latestUpdateWeb" />
    <n-divider />

    <n-space align="center" justify="space-between">
      <n-h2 prefix="bar" style="margin-bottom: 30px">最新更新-文库小说</n-h2>
      <n-a href="/wenku-list">更多</n-a>
    </n-space>
    <PanelWenkuNovel :list="latestUpdateWenku" />
    <n-divider />
  </MainLayout>
</template>
