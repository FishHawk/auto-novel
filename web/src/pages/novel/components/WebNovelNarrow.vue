<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WebNovelDto, WebNovelTocItemDto } from '@/model/WebNovel';

import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const { isSignedIn } = Locator.userDataRepository();
const setting = Locator.settingRepository().ref;

const lastReadChapter = computed(() => {
  const { novel } = props;
  if (novel.lastReadChapterId) {
    return novel.toc.find((it) => it.chapterId === novel.lastReadChapterId);
  }
});

const toc = computed(() => {
  const { novel } = props;
  const novelToc = novel.toc as ReadableTocItem[];
  let order = 0;
  for (const it of novelToc) {
    it.order = it.chapterId ? order : undefined;
    if (it.chapterId) order += 1;
  }
  return novelToc;
});

const showCatalogDrawer = ref(false);
</script>

<template>
  <web-novel-metadata
    :provider-id="providerId"
    :novel-id="novelId"
    :novel="novel"
  />
  <section-header title="翻译" />
  <web-translate
    v-if="isSignedIn"
    :provider-id="providerId"
    :novel-id="novelId"
    :title-jp="novel.titleJp"
    :title-zh="novel.titleZh"
    :total="novel.toc.filter((it: WebNovelTocItemDto) => it.chapterId).length"
    v-model:jp="novel.jp"
    v-model:baidu="novel.baidu"
    v-model:youdao="novel.youdao"
    v-model:gpt="novel.gpt"
    :sakura="novel.sakura"
    :glossary="novel.glossary"
  />
  <n-p v-else>游客无法使用该功能，请先登录。</n-p>

  <section-header title="目录">
    <c-button
      v-if="novel.toc.length >= 20"
      label="展开"
      @action="showCatalogDrawer = true"
    />
    <c-button
      :label="setting.tocSortReverse ? '倒序' : '正序'"
      :icon="SortOutlined"
      @action="setting.tocSortReverse = !setting.tocSortReverse"
    />
  </section-header>

  <n-list>
    <n-card
      v-if="lastReadChapter"
      :bordered="false"
      embedded
      style="margin-bottom: 8px"
      content-style="padding: 6px 0px 0px;"
    >
      <b style="padding-left: 6px">上次读到:</b>
      <web-novel-toc-item
        :provider-id="providerId"
        :novel-id="novelId"
        :toc-item="lastReadChapter"
        :last-read="novel.lastReadChapterId"
      />
    </n-card>
    <n-list-item
      v-for="tocItem in setting.tocSortReverse
        ? toc.slice().reverse().slice(0, 20)
        : toc.slice(0, 20)"
      :key="`${tocItem.chapterId}/${tocItem.titleJp}`"
      style="padding: 0px"
    >
      <web-novel-toc-item
        :provider-id="providerId"
        :novel-id="novelId"
        :toc-item="tocItem"
        :last-read="novel.lastReadChapterId"
      />
    </n-list-item>
  </n-list>

  <c-button
    v-if="novel.toc.length >= 20"
    secondary
    label="展开"
    @action="showCatalogDrawer = true"
    style="width: 100%"
  />

  <c-drawer-right
    v-if="novel.toc.length >= 20"
    :width="320"
    v-model:show="showCatalogDrawer"
    title="目录"
    display-directive="show"
  >
    <template #action>
      <c-button
        :label="setting.tocSortReverse ? '倒序' : '正序'"
        :icon="SortOutlined"
        @action="setting.tocSortReverse = !setting.tocSortReverse"
      />
    </template>
    <n-list style="padding: 12px">
      <n-list-item
        v-for="tocItem in setting.tocSortReverse ? toc.slice().reverse() : toc"
        :key="`${tocItem.chapterId}/${tocItem.titleJp}`"
        style="padding: 0px"
      >
        <web-novel-toc-item
          :provider-id="providerId"
          :novel-id="novelId"
          :toc-item="tocItem"
          :last-read="novel.lastReadChapterId"
          show-last-read
        />
      </n-list-item>
    </n-list>
  </c-drawer-right>

  <CommentList :site="`web-${providerId}-${novelId}`" />
</template>
