<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
} from '@vicons/material';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import ApiWebNovel, { BookMetadataDto } from '@/data/api/api_web_novel';
import { buildMetadataUrl } from '@/data/provider';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const bookId = route.params.bookId as string;
const url = buildMetadataUrl(providerId, bookId);

const bookMetadata = ref<ResultState<BookMetadataDto>>();

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiWebNovel.getMetadata(
    providerId,
    bookId,
    authInfoStore.token
  );
  bookMetadata.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
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

  const result = await ApiWebNovel.addFavorite(providerId, bookId, token);
  if (result.ok) {
    if (bookMetadata.value?.ok) {
      bookMetadata.value.value.inFavorite = true;
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

  const result = await ApiWebNovel.removeFavorite(providerId, bookId, token);
  if (result.ok) {
    if (bookMetadata.value?.ok) {
      bookMetadata.value.value.inFavorite = false;
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
    <div v-if="bookMetadata?.ok">
      <n-h1 prefix="bar" style="font-size: 22px">
        <n-a :href="url" target="_blank">{{ bookMetadata.value.titleJp }}</n-a>
        <br />
        <span style="color: grey">{{ bookMetadata.value.titleZh }}</span>
      </n-h1>

      <n-p v-if="bookMetadata.value.authors.length > 0">
        作者：
        <span v-for="author in bookMetadata.value.authors">
          <n-a :href="author.link" target="_blank">{{ author.name }}</n-a>
        </span>
      </n-p>

      <n-p>
        <n-space>
          <span>浏览次数:{{ bookMetadata.value.visited }}</span>
          <span>下载次数:{{ bookMetadata.value.downloaded }}</span>
        </n-space>
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

        <n-button
          v-if="bookMetadata.value.inFavorite === true"
          @click="removeFavorite()"
        >
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
      </n-space>

      <template v-if="editMode">
        <WebEditSection
          :provider-id="providerId"
          :book-id="bookId"
          v-model:book-metadata="bookMetadata.value"
        />
      </template>

      <template v-else>
        <n-p style="word-break: break-all">
          {{ bookMetadata.value.introductionJp }}
        </n-p>
        <n-p
          v-if="bookMetadata.value.introductionZh !== undefined"
          style="word-break: break-all"
        >
          {{ bookMetadata.value.introductionZh }}
        </n-p>
        <TranslateSection
          :provider-id="providerId"
          :book-id="bookId"
          :glossary="bookMetadata.value.glossary"
        />
        <TocSection
          :provider-id="providerId"
          :book-id="bookId"
          :toc="bookMetadata.value.toc"
        />
        <CommentList :post-id="route.path" />
      </template>
    </div>

    <div v-if="bookMetadata && !bookMetadata.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="bookMetadata.error.message"
      />
    </div>
  </MainLayout>
</template>
