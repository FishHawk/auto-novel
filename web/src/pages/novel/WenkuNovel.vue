<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import { useMessage, useThemeVars } from 'naive-ui';
import {
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
} from '@vicons/material';

import coverPlaceholder from '@/images/cover_placeholder.png';
import { ResultState } from '@/data/result';
import { ApiWenkuNovel, WenkuMetadataDto } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();
const message = useMessage();

const route = useRoute();
const novelId = route.params.novelId as string;

const novelMetadataResult = ref<ResultState<WenkuMetadataDto>>();

async function getMetadata() {
  const result = await ApiWenkuNovel.getNovel(novelId);
  novelMetadataResult.value = result;
  if (result.ok) {
    document.title = result.value.title;
  }
}
getMetadata();

async function refreshMetadata() {
  const result = await ApiWenkuNovel.getNovel(novelId);
  if (result.ok) {
    novelMetadataResult.value = result;
  }
}

var isFavoriteChanging = false;

async function addFavorite() {
  if (isFavoriteChanging) return;
  isFavoriteChanging = true;

  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }

  const result = await ApiWenkuNovel.putFavored(novelId);
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

  if (!userData.isLoggedIn) {
    message.info('请先登录');
    return;
  }

  const result = await ApiWenkuNovel.deleteFavored(novelId);
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

function sortVolumesZh(volumes: string[]) {
  return volumes.sort((a, b) => a.localeCompare(b));
}
const vars = useThemeVars();
</script>

<template>
  <MainLayout>
    <template v-slot:full-width>
      <div
        v-if="novelMetadataResult?.ok"
        :style="{
          background:
            `linear-gradient(color-mix(in srgb, ${vars.bodyColor} 40%, transparent), ${vars.bodyColor}), ` +
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
          <n-layout class="container" style="background-color: transparent">
            <n-space
              :wrap="false"
              style="padding-top: 40px; padding-bottom: 20px; min-height: 260px"
            >
              <n-card size="small" style="width: 160px">
                <template #cover>
                  <img
                    :src="
                      novelMetadataResult.value.cover
                        ? novelMetadataResult.value.cover
                        : coverPlaceholder
                    "
                    alt="cover"
                  />
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
          </n-layout>
        </div>
      </div>
    </template>

    <ResultView
      :result="novelMetadataResult"
      :showEmpty="() => false"
      v-slot="{ value: metadata }"
    >
      <n-space>
        <RouterNA :to="`/wenku-edit/${novelId}`">
          <n-button>
            <template #icon>
              <n-icon :component="EditNoteFilled" />
            </template>
            编辑
          </n-button>
        </RouterNA>

        <AsyncButton
          v-if="metadata.favored === true"
          :on-async-click="removeFavorite"
        >
          <template #icon>
            <n-icon :component="FavoriteFilled" />
          </template>
          取消收藏
        </AsyncButton>

        <AsyncButton v-else :on-async-click="addFavorite">
          <template #icon>
            <n-icon :component="FavoriteBorderFilled" />
          </template>
          收藏
        </AsyncButton>
      </n-space>

      <template v-if="editMode">
        <WenkuEdit :id="novelId" :metadata="metadata" />
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
        <UploadButton
          type="zh"
          :novelId="novelId"
          @uploadFinished="refreshMetadata()"
        />
        <n-ul>
          <n-li v-for="fileName in sortVolumesZh(metadata.volumeZh)">
            <n-a
              :href="`/files-wenku/${novelId}/${fileName}`"
              target="_blank"
              :download="fileName"
            >
              {{ fileName }}
            </n-a>
          </n-li>
        </n-ul>

        <section>
          <SectionHeader title="日文章节" />
          <UploadButton
            type="jp"
            :novelId="novelId"
            @uploadFinished="refreshMetadata()"
          />
          <WenkuTranslate
            :novel-id="novelId"
            :glossary="metadata.glossary"
            :volumes="metadata.volumeJp"
            @deleted="refreshMetadata()"
          />
        </section>

        <CommentList :site="`wenku-${novelId}`" />
      </template>
    </ResultView>
  </MainLayout>
</template>
