<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WebNovelDto, WebNovelTocItemDto } from '@/model/WebNovel';

import { checkIsMobile } from '@/pages/util';
import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const isMobile = checkIsMobile();

const { isSignedIn } = Locator.userDataRepository();
const setting = Locator.settingRepository().ref;

const displayTocItemSize = 5;

const toc = computed(() => {
  const { novel } = props;
  const novelToc = novel.toc as ReadableTocItem[];
  let order = 0;
  for (const [index, it] of novelToc.entries()) {
    it.key = index;
    it.order = it.chapterId ? order : undefined;
    if (it.chapterId) order += 1;
  }
  return novelToc;
});

const lastReadChapter = computed(() => {
  const { novel } = props;
  if (novel.lastReadChapterId) {
    return toc.value.find((it) => it.chapterId === novel.lastReadChapterId);
  }
});
const startReadChapter = computed(() => {
  if (lastReadChapter.value !== undefined) {
    return lastReadChapter.value;
  }
  return toc.value.find((it) => it.chapterId !== undefined);
});

const showCatalogDrawer = ref(false);
const showTranslateSection = ref(!isMobile);
</script>

<template>
  <web-novel-metadata
    :provider-id="providerId"
    :novel-id="novelId"
    :novel="novel"
  />

  <n-divider />

  <c-button
    v-if="isMobile"
    :label="showTranslateSection ? '折叠翻译' : '展开翻译'"
    @action="showTranslateSection = !showTranslateSection"
  />

  <n-collapse-transition v-if="isSignedIn" :show="showTranslateSection">
    <web-translate
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
  </n-collapse-transition>
  <n-p v-else>游客无法使用该功能，请先登录。</n-p>

  <n-divider />

  <web-novel-toc-item
    v-if="startReadChapter !== undefined"
    :provider-id="providerId"
    :novel-id="novelId"
    :toc-item="startReadChapter"
    :last-read="novel.lastReadChapterId"
  />
  <c-button
    v-if="novel.toc.length > 1"
    secondary
    label="展开目录"
    @action="showCatalogDrawer = true"
    style="width: 100%"
  />

  <c-drawer-right
    v-if="novel.toc.length >= displayTocItemSize"
    :width="320"
    v-model:show="showCatalogDrawer"
    title="目录"
  >
    <template #action>
      <c-button
        :label="setting.tocSortReverse ? '倒序' : '正序'"
        :icon="SortOutlined"
        @action="setting.tocSortReverse = !setting.tocSortReverse"
      />
    </template>

    <n-virtual-list
      :item-size="78"
      :items="setting.tocSortReverse ? toc.slice().reverse() : toc"
      item-resizable
      :default-scroll-key="lastReadChapter?.key"
      :scrollbar-props="{ trigger: 'none' }"
      style="height: calc(100vh - 68px)"
    >
      <template #default="{ item }">
        <div :key="item.key" style="padding-left: 8px; padding-right: 8px">
          <web-novel-toc-item
            :provider-id="providerId"
            :novel-id="novelId"
            :toc-item="item"
            :last-read="novel.lastReadChapterId"
          />
        </div>
      </template>
    </n-virtual-list>
  </c-drawer-right>

  <CommentList :site="`web-${providerId}-${novelId}`" />
</template>
