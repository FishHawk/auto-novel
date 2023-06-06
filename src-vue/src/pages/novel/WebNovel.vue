<script lang="ts" setup>
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
  BookFilled,
} from '@vicons/material';
import { useMessage } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import ApiUser from '@/data/api/api_user';
import { ApiWebNovel, WebNovelMetadataDto } from '@/data/api/api_web_novel';
import { buildMetadataUrl } from '@/data/provider';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const authInfoStore = useAuthInfoStore();

const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;
const url = buildMetadataUrl(providerId, novelId);

const metadata = ref<ResultState<WebNovelMetadataDto>>();

onMounted(() => getMetadata());
async function getMetadata() {
  const result = await ApiWebNovel.getMetadata(
    providerId,
    novelId,
    authInfoStore.token
  );
  metadata.value = result;
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

  const result = await ApiUser.putFavoritedWebNovel(providerId, novelId, token);
  if (result.ok) {
    if (metadata.value?.ok) {
      metadata.value.value.favored = true;
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

  const result = await ApiUser.deleteFavoritedWebNovel(
    providerId,
    novelId,
    token
  );
  if (result.ok) {
    if (metadata.value?.ok) {
      metadata.value.value.favored = false;
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
    <div v-if="metadata?.ok">
      <n-h1 prefix="bar" style="font-size: 22px">
        <n-a :href="url" target="_blank">{{ metadata.value.titleJp }}</n-a>
        <br />
        <span style="color: grey">{{ metadata.value.titleZh }}</span>
      </n-h1>

      <n-p v-if="metadata.value.authors.length > 0">
        作者：
        <span v-for="author in metadata.value.authors">
          <n-a :href="author.link" target="_blank">{{ author.name }}</n-a>
        </span>
        <span style="margin-left: 20px">
          浏览次数:{{ metadata.value.visited }}
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

        <n-button
          v-if="metadata.value.favored === true"
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

        <n-a
          v-if="metadata.value.wenkuId"
          :href="`/wenku/${metadata.value.wenkuId}`"
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

      <template v-if="editMode">
        <WebEditSection
          :provider-id="providerId"
          :novel-id="novelId"
          v-model:novel-metadata="metadata.value"
        />
      </template>

      <template v-else>
        <n-p style="word-break: break-all">
          {{ metadata.value.introductionJp }}
        </n-p>
        <n-p
          v-if="metadata.value.introductionZh !== undefined"
          style="word-break: break-all"
        >
          {{ metadata.value.introductionZh }}
        </n-p>

        <n-h2 prefix="bar">翻译</n-h2>
        <n-p>
          网页端翻译需要安装插件，请查看
          <n-a href="/how-to-use" target="_blank">使用说明</n-a>。
          移动端暂时无法翻译。
        </n-p>
        <WebTranslate
          :provider-id="providerId"
          :novel-id="novelId"
          :total="metadata.value.toc.filter((it) => it.chapterId).length"
          v-model:jp="metadata.value.jp"
          v-model:baidu="metadata.value.baidu"
          v-model:youdao="metadata.value.youdao"
          :glossary="metadata.value.glossary"
        />

        <TocSection
          :provider-id="providerId"
          :novel-id="novelId"
          :toc="metadata.value.toc"
        />
        <CommentList :post-id="route.path" />
      </template>
    </div>

    <div v-if="metadata && !metadata.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="metadata.error.message"
      />
    </div>
  </MainLayout>
</template>
