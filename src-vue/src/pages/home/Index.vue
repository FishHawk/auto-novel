<script lang="ts" setup>
import { ref } from 'vue';
import { useRouter } from 'vue-router';

import { ApiUser } from '@/data/api/api_user';
import { ApiWebNovel, WebNovelOutlineDto } from '@/data/api/api_web_novel';
import {
  ApiWenkuNovel,
  WenkuNovelOutlineDto,
} from '@/data/api/api_wenku_novel';
import { parseUrl } from '@/data/provider';
import { Ok, ResultState } from '@/data/api/result';
import { useUserDataStore } from '@/data/stores/userData';
import { useIsDesktop } from '@/data/util';

const isDesktop = useIsDesktop(900);
const router = useRouter();

const url = ref('');
function query(url: string) {
  if (url.length === 0) return;
  const parseResult = parseUrl(url);
  if (parseResult !== undefined) {
    const { providerId, novelId } = parseResult;
    router.push({ path: `/novel/${providerId}/${novelId}` });
  } else {
    router.push({ path: `/novel-list`, query: { query: url } });
  }
}

const userData = useUserDataStore();
const favoriteList = ref<ResultState<WebNovelOutlineDto[]>>();
async function loadFavorite() {
  const result = await ApiUser.listFavoritedWebNovel(0, 8, 'update');
  if (result.ok) {
    favoriteList.value = Ok(result.value.items);
  } else {
    favoriteList.value = result;
  }
}
loadFavorite();

const latestUpdateWeb = ref<ResultState<WebNovelOutlineDto[]>>();
async function loadLatestUpdateWeb() {
  const result = await ApiWebNovel.list(0, 8, '', '', 0, 0, 0);
  if (result.ok) {
    latestUpdateWeb.value = Ok(result.value.items);
  } else {
    latestUpdateWeb.value = result;
  }
}
loadLatestUpdateWeb();

const latestUpdateWenku = ref<ResultState<WenkuNovelOutlineDto[]>>();
async function loadLatestUpdateWenku() {
  const result = await ApiWenkuNovel.list(0, '');
  if (result.ok) {
    latestUpdateWenku.value = Ok(result.value.items.slice(0, 12));
  } else {
    latestUpdateWenku.value = result;
  }
}
loadLatestUpdateWenku();
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        style="
          background: url('https://images.unsplash.com/photo-1521587760476-6c12a4b040da?ixlib=rb-4.0.3&ixid=MnwxMjA3fDB8MHxwaG90by1wYWdlfHx8fGVufDB8fHx8&auto=format&fit=crop&w=1170&q=80');
          width: 100%;
          padding-top: 60px;
          padding-bottom: 104px;
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
            轻小说机翻机器人
          </n-h1>
          <n-input-group>
            <n-input
              v-model:value="url"
              size="large"
              placeholder="请输入小说链接，或者输入标题搜索本站缓存..."
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
        <div style="flex: 1; margin-right: 20px">
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

    <template v-if="userData.logined">
      <SectionHeader title="我的收藏">
        <n-a href="/favorite-list">更多</n-a>
      </SectionHeader>
      <PanelWebNovel :list-result="favoriteList" />
      <n-divider />
    </template>

    <SectionHeader title="最新更新-网络小说">
      <n-a href="/novel-list">更多</n-a>
    </SectionHeader>
    <PanelWebNovel :list-result="latestUpdateWeb" />
    <n-divider />

    <SectionHeader title="最新更新-文库小说" style="margin-bottom: 20px">
      <n-a href="/wenku-list">更多</n-a>
    </SectionHeader>
    <PanelWenkuNovel :list-result="latestUpdateWenku" />
    <n-divider />
  </MainLayout>
</template>
