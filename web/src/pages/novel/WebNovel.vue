<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';
import {
  EditNoteFilled,
  FavoriteBorderFilled,
  FavoriteFilled,
  BookFilled,
  SortFilled,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { createReusableTemplate } from '@vueuse/core';

import { Ok, ResultState } from '@/data/api/result';
import {
  ApiWebNovel,
  WebNovelMetadataDto,
  WebNovelTocItemDto,
} from '@/data/api/api_web_novel';
import { buildMetadataUrl, tryTranslateKeyword } from '@/data/provider';
import { useUserDataStore } from '@/data/stores/userData';
import { useSettingStore } from '@/data/stores/setting';

const [DefineTag, ReuseTag] = createReusableTemplate<{
  tag: string;
  attention: boolean;
}>();

const setting = useSettingStore();
const userData = useUserDataStore();
const message = useMessage();

const route = useRoute();
const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;

const metadataResult = ref<ResultState<WebNovelMetadataDto>>();

async function getMetadata() {
  const result = await ApiWebNovel.getMetadata(providerId, novelId);
  metadataResult.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
  }
}
getMetadata();

async function addFavorite() {
  const result = await ApiWebNovel.putFavored(providerId, novelId);
  if (result.ok) {
    if (metadataResult.value?.ok) {
      metadataResult.value.value.favored = true;
    }
  } else {
    message.error('收藏错误：' + result.error.message);
  }
}

async function removeFavorite() {
  const result = await ApiWebNovel.deleteFavored(providerId, novelId);
  if (result.ok) {
    if (metadataResult.value?.ok) {
      metadataResult.value.value.favored = false;
    }
  } else {
    message.error('取消收藏错误：' + result.error.message);
  }
}

const editMode = ref(false);
function toggleEditMode() {
  if (!userData.logined) {
    return message.info('请先登录');
  }
  editMode.value = !editMode.value;
}
</script>

<template>
  <DefineTag v-slot="{ tag, attention }">
    <router-link
      :to="`/novel-list?query=${tag}\$`"
      style="color: rgb(51, 54, 57)"
    >
      <n-tag :bordered="false" size="small">
        <template v-if="attention">
          <b>{{ tag }}</b>
        </template>
        <template v-else>
          {{ tryTranslateKeyword(tag) }}
        </template>
      </n-tag>
    </router-link>
  </DefineTag>

  <MainLayout>
    <ResultView
      :result="metadataResult"
      :showEmpty="() => false"
      v-slot="{ value: metadata }"
    >
      <n-h1 prefix="bar" style="font-size: 22px">
        <n-a :href="buildMetadataUrl(providerId, novelId)">{{
          metadata.titleJp
        }}</n-a>
        <br />
        <n-text depth="3">{{ metadata.titleZh }}</n-text>
      </n-h1>

      <n-p v-if="metadata.authors.length > 0">
        作者：
        <template v-for="author in metadata.authors">
          <n-a :href="author.link">{{ author.name }}</n-a>
        </template>
      </n-p>

      <n-space>
        <n-button @click="toggleEditMode()">
          <template #icon>
            <n-icon :component="EditNoteFilled" />
          </template>
          {{ editMode ? '退出编辑' : '编辑' }}
        </n-button>

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

        <router-link v-if="metadata.wenkuId" :to="`/wenku/${metadata.wenkuId}`">
          <n-button>
            <template #icon>
              <n-icon :component="BookFilled" />
            </template>
            文库版
          </n-button>
        </router-link>
      </n-space>

      <WebEdit
        v-if="editMode"
        :provider-id="providerId"
        :novel-id="novelId"
        :novel-metadata="metadata"
        @update:novel-metadata="metadataResult = Ok(metadata)"
      />

      <template v-else>
        <n-p>{{ metadata.type }} / 浏览次数:{{ metadata.visited }}</n-p>

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
          <ReuseTag
            v-for="attention of metadata.attentions.sort()"
            :tag="attention"
            :attention="true"
          />
          <ReuseTag
            v-for="keyword of metadata.keywords"
            :tag="keyword"
            :attention="false"
          />
        </n-space>

        <section>
          <SectionHeader title="翻译" />
          <WebTranslate
            :provider-id="providerId"
            :novel-id="novelId"
            :title-jp="metadata.titleJp"
            :title-zh="metadata.titleZh"
            :total="metadata.toc.filter((it: WebNovelTocItemDto) => it.chapterId).length"
            v-model:jp="metadata.jp"
            v-model:baidu="metadata.baidu"
            v-model:youdao="metadata.youdao"
            v-model:gpt="metadata.gpt"
            :glossary="metadata.glossary"
          />
        </section>

        <section>
          <SectionHeader title="目录">
            <n-button @click="setting.tocSortReverse = !setting.tocSortReverse">
              <template #icon>
                <n-icon :component="SortFilled" />
              </template>
              {{ setting.tocSortReverse ? '倒序' : '正序' }}
            </n-button>
          </SectionHeader>
          <SectionWebToc
            :provider-id="providerId"
            :novel-id="novelId"
            :toc="metadata.toc"
            :reverse="setting.tocSortReverse"
            :last-read-chapter-id="metadata.lastReadChapterId"
          />
        </section>

        <CommentList :site="`web-${providerId}-${novelId}`" />
      </template>
    </ResultView>
  </MainLayout>
</template>