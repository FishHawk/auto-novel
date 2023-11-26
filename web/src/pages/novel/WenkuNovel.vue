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
import { ApiWenkuNovel, WenkuNovelDto } from '@/data/api/api_wenku_novel';
import { useUserDataStore } from '@/data/stores/user_data';

const userData = useUserDataStore();
const message = useMessage();

const route = useRoute();
const novelId = route.params.novelId as string;

const novelMetadataResult = ref<ResultState<WenkuNovelDto>>();

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
                  <n-text v-if="novelMetadataResult.value.r18" depth="3">
                    [成人]
                  </n-text>
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

        <favorite-button
          :favored="metadata.favored"
          :favored-list="metadata.favoredList"
          :novel="{ type: 'wenku', novelId }"
          @update:favored="getMetadata"
        />

        <n-a
          :href="`https://www.amazon.co.jp/s?k=${metadata.title}&rh=n%3A465392`"
          target="_blank"
        >
          <n-button>在亚马逊搜索</n-button>
        </n-a>
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

        <SectionHeader v-if="metadata.volumes.length" title="各卷封面" />
        <div v-if="metadata.volumes.length">
          <n-scrollbar x-scrollable>
            <div style="white-space: nowrap">
              <n-card
                v-for="volume of metadata.volumes"
                size="small"
                header-style="padding: 8px;"
                :bordered="false"
                :wrap="false"
                style="
                  display: inline-block;
                  width: 100px;
                  margin: 4px;
                  padding-bottom: 12px;
                "
              >
                <template #cover>
                  <img
                    :src="volume.cover"
                    alt="cover"
                    style="aspect-ratio: 1 / 1.5; object-fit: cover"
                  />
                </template>
              </n-card>
            </div>
          </n-scrollbar>
        </div>

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

        <CommentList :site="`wenku-${novelId}`" />
      </template>
    </ResultView>
  </MainLayout>
</template>
