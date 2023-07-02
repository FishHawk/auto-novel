<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage } from 'naive-ui';
import {
  DoorbellFilled,
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
} from '@vicons/material';

import { Ok, ResultState } from '@/data/api/result';
import { ApiUser } from '@/data/api/api_user';
import { ApiWenkuNovel, WenkuMetadataDto } from '@/data/api/api_wenku_novel';
import { useAuthInfoStore, atLeastMaintainer } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();
const message = useMessage();

const route = useRoute();
const novelId = route.params.novelId as string;

const novelMetadataResult = ref<ResultState<WenkuMetadataDto>>();

async function getMetadata() {
  const result = await ApiWenkuNovel.getMetadata(novelId, authInfoStore.token);
  novelMetadataResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
}
getMetadata();

async function refreshMetadata() {
  const result = await ApiWenkuNovel.getMetadata(novelId, authInfoStore.token);
  if (result.ok) {
    novelMetadataResult.value = result;
  }
}

var isFavoriteChanging = false;

async function addFavorite() {
  if (isFavoriteChanging) return;
  isFavoriteChanging = true;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  const result = await ApiUser.putFavoritedWenkuNovel(novelId, token);
  if (result.ok) {
    if (novelMetadataResult.value?.ok) {
      novelMetadataResult.value.value.favored = true;
    }
  } else {
    message.error('收藏错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}

async function removeFavorite() {
  if (isFavoriteChanging) return;
  isFavoriteChanging = true;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  const result = await ApiUser.deleteFavoritedWenkuNovel(novelId, token);
  if (result.ok) {
    if (novelMetadataResult.value?.ok) {
      novelMetadataResult.value.value.favored = false;
    }
  } else {
    message.error('取消收藏错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}

const editMode = ref(false);
function enableEditMode() {
  if (!atLeastMaintainer(authInfoStore.role)) {
    message.info('权限不够');
    return;
  }
  editMode.value = true;
}

async function notifyUpdate() {
  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  const result = await ApiWenkuNovel.notifyUpdate(novelId, token);
  if (result.ok) {
    message.info('提醒更新成功');
  } else {
    message.error('提醒更新错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        v-if="novelMetadataResult?.ok"
        :style="{
          background:
            'linear-gradient( to top, rgba(255, 255, 255, 1), rgba(255, 255, 255, 0.4)), ' +
            `url(${novelMetadataResult.value.cover})`,
        }"
        style="
          width: 100%;
          clip: rect(0, auto, auto, 0);
          background-size: cover;
          background-position: center 15%;
        "
      >
        <div style="width: 100%; height: 100%; backdrop-filter: blur(4px)">
          <div class="container" style="filter: ">
            <n-space
              :wrap="false"
              style="padding-top: 40px; padding-bottom: 20px; min-height: 260px"
            >
              <n-card size="small" style="width: 160px">
                <template #cover>
                  <img :src="novelMetadataResult.value.cover" alt="cover" />
                </template>
              </n-card>
              <div>
                <n-h1 prefix="bar" style="font-size: 22px; font-weight: 900">
                  {{
                    novelMetadataResult.value.titleZh
                      ? novelMetadataResult.value.titleZh
                      : novelMetadataResult.value.title
                  }}
                </n-h1>

                <table style="border-spacing: 0px 8px">
                  <TagGroup
                    v-if="novelMetadataResult.value.authors.length > 0"
                    label="作者"
                    :tags="novelMetadataResult.value.authors"
                  />
                  <TagGroup
                    v-if="novelMetadataResult.value.artists.length > 0"
                    label="插图"
                    :tags="novelMetadataResult.value.artists"
                  />
                </table>
              </div>
            </n-space>
          </div>
        </div>
      </div>
    </template>

    <ResultView :result="novelMetadataResult" v-slot="{ value: metadata }">
      <n-space>
        <templage v-if="atLeastMaintainer(authInfoStore.role)">
          <n-button v-if="!editMode" @click="enableEditMode()">
            <template #icon>
              <n-icon> <EditNoteFilled /> </n-icon>
            </template>
            编辑
          </n-button>

          <n-button v-else @click="editMode = false">
            <template #icon>
              <n-icon> <EditNoteFilled /> </n-icon>
            </template>
            退出编辑
          </n-button>
        </templage>

        <n-button v-if="metadata.favored === true" @click="removeFavorite()">
          <template #icon>
            <n-icon> <FavoriteFilled /> </n-icon>
          </template>
          取消收藏
        </n-button>

        <n-button v-else @click="addFavorite()">
          <template #icon>
            <n-icon> <FavoriteBorderFilled /> </n-icon>
          </template>
          收藏
        </n-button>

        <n-button
          v-if="atLeastMaintainer(authInfoStore.role)"
          @click="notifyUpdate()"
        >
          <template #icon>
            <n-icon> <DoorbellFilled /> </n-icon>
          </template>
          提醒更新
        </n-button>
      </n-space>

      <template v-if="editMode">
        <WenkuEditSection :id="novelId" :metadata="metadata" />
      </template>

      <template v-else>
        <n-p>原名：{{ metadata.title }}</n-p>
        <n-p v-html="metadata.introduction.replace(/\n/g, '<br />')" />

        <n-space :size="[4, 4]">
          <n-tag
            v-for="tag of metadata.keywords"
            :bordered="false"
            size="small"
          >
            {{ tag }}
          </n-tag>
        </n-space>

        <SectionHeader title="中文章节" />
        <ListVolumes
          type="zh"
          :volumesResult="Ok(metadata.volumeZh)"
          :novelId="novelId"
          @uploadFinished="refreshMetadata()"
        />

        <SectionHeader title="日文章节" />
        <ListVolumes
          type="jp"
          :volumesResult="Ok(metadata.volumeJp)"
          :novelId="novelId"
          @uploadFinished="refreshMetadata()"
        />

        <SectionComment :post-id="route.path" />
      </template>
    </ResultView>
  </MainLayout>
</template>
