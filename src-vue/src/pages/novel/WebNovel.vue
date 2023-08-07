<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
  BookFilled,
} from '@vicons/material';
import { useMessage } from 'naive-ui';

import { Ok, ResultState } from '@/data/api/result';
import { ApiUser } from '@/data/api/api_user';
import {
  ApiWebNovel,
  WebNovelMetadataDto,
  WebNovelTocItemDto,
} from '@/data/api/api_web_novel';
import { buildMetadataUrl } from '@/data/provider';
import { useAuthInfoStore } from '@/data/stores/authInfo';
import { tryTranslateKeywords } from '@/data/keyword_translate';

const authInfoStore = useAuthInfoStore();
const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;
const url = buildMetadataUrl(providerId, novelId);

const metadataResult = ref<ResultState<WebNovelMetadataDto>>();

async function getMetadata() {
  const result = await ApiWebNovel.getMetadata(
    providerId,
    novelId,
    authInfoStore.token
  );
  metadataResult.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
  }
}
getMetadata();

let isFavoriteChanging = false;

async function addFavorite() {
  if (isFavoriteChanging) return;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  isFavoriteChanging = true;
  const result = await ApiUser.putFavoritedWebNovel(providerId, novelId, token);
  if (result.ok) {
    if (metadataResult.value?.ok) {
      metadataResult.value.value.favored = true;
    }
  } else {
    message.error('收藏错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}

async function removeFavorite() {
  if (isFavoriteChanging) return;

  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }

  isFavoriteChanging = true;
  const result = await ApiUser.deleteFavoritedWebNovel(
    providerId,
    novelId,
    token
  );
  if (result.ok) {
    if (metadataResult.value?.ok) {
      metadataResult.value.value.favored = false;
    }
  } else {
    message.error('取消收藏错误：' + result.error.message);
  }
  isFavoriteChanging = false;
}

const editMode = ref(false);
function enableEditMode() {
  const token = authInfoStore.token;
  if (!token) {
    message.info('请先登录');
    return;
  }
  editMode.value = true;
}
</script>

<template>
  <MainLayout>
    <ResultView
      :result="metadataResult"
      :showEmpty="() => false"
      v-slot="{ value: metadata }"
    >
      <n-h1 prefix="bar" style="font-size: 22px">
        <n-a :href="url" target="_blank">{{ metadata.titleJp }}</n-a>
        <br />
        <span style="color: grey">{{ metadata.titleZh }}</span>
      </n-h1>

      <n-p v-if="metadata.authors.length > 0">
        作者：
        <span v-for="author in metadata.authors">
          <n-a :href="author.link" target="_blank">{{ author.name }}</n-a>
        </span>
      </n-p>

      <n-space>
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

        <n-a
          v-if="metadata.wenkuId"
          :href="`/wenku/${metadata.wenkuId}`"
          target="_blank"
        >
          <n-button>
            <template #icon>
              <n-icon> <BookFilled /> </n-icon>
            </template>
            文库版
          </n-button>
        </n-a>
      </n-space>

      <n-p>{{ metadata.type }} / 浏览次数:{{ metadata.visited }} </n-p>

      <template v-if="editMode">
        <WebEditSection
          :provider-id="providerId"
          :novel-id="novelId"
          :novel-metadata="metadata"
          @update:novel-metadata="metadataResult = Ok(metadata)"
        />
      </template>

      <template v-else>
        <n-p style="word-break: break-all">
          {{ metadata.introductionJp }}
        </n-p>
        <n-p
          v-if="metadata.introductionZh !== undefined"
          style="word-break: break-all"
        >
          {{ metadata.introductionZh }}
        </n-p>

        <n-space :size="[4, 4]">
          <n-tag
            v-for="tag of metadata.attentions"
            :bordered="false"
            size="small"
          >
            <b>{{ tag }}</b>
          </n-tag>
          <n-tag
            v-for="tag of tryTranslateKeywords(metadata.keywords)"
            :bordered="false"
            size="small"
          >
            {{ tag }}
          </n-tag>
        </n-space>

        <SectionWebTranslate
          :provider-id="providerId"
          :novel-id="novelId"
          :title="metadata.titleZh ?? metadata.titleJp"
          :total="metadata.toc.filter((it: WebNovelTocItemDto) => it.chapterId).length"
          v-model:jp="metadata.jp"
          v-model:baidu="metadata.baidu"
          v-model:youdao="metadata.youdao"
          :gpt="metadata.gpt"
          :glossary="metadata.glossary"
        />
        <SectionWebToc
          :provider-id="providerId"
          :novel-id="novelId"
          :toc="metadata.toc"
          :last-read-chapter-id="metadata.lastReadChapterId"
        />
        <SectionComment :post-id="route.path" />
      </template>
    </ResultView>
  </MainLayout>
</template>
