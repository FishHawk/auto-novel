<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WebNovelDto, WebNovelTocItemDto } from '@/model/WebNovel';

import { useToc, useLastReadChapter } from './UseWebNovel';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const { setting } = Locator.settingRepository();

const { toc } = useToc(props.novel);
const { lastReadChapter } = useLastReadChapter(props.novel, toc);
const startReadChapter = computed(() => {
  if (lastReadChapter.value !== undefined) {
    return lastReadChapter.value;
  }
  return toc.value.find((it) => it.chapterId !== undefined);
});

const showCatalogDrawer = ref(false);
</script>

<template>
  <web-novel-metadata
    :provider-id="providerId"
    :novel-id="novelId"
    :novel="novel"
  />

  <n-divider />

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

  <n-divider />

  <template v-if="setting.tocCollapseInNarrowScreen">
    <chapter-toc-item
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
  </template>
  <template v-else>
    <c-button
      :label="setting.tocSortReverse ? '倒序' : '正序'"
      :icon="SortOutlined"
      @action="setting.tocSortReverse = !setting.tocSortReverse"
      style="margin-bottom: 16px"
    />
    <n-list>
      <n-card
        v-if="lastReadChapter !== undefined"
        :bordered="false"
        embedded
        style="margin-bottom: 8px"
        content-style="padding: 6px 0px 0px;"
      >
        <b style="padding-left: 6px">上次读到:</b>
        <chapter-toc-item
          :provider-id="providerId"
          :novel-id="novelId"
          :toc-item="lastReadChapter"
          :last-read="novel.lastReadChapterId"
        />
      </n-card>
      <n-list-item
        v-for="tocItem in setting.tocSortReverse ? toc.slice().reverse() : toc"
        :key="tocItem.key"
        style="padding: 0px"
      >
        <chapter-toc-item
          :provider-id="providerId"
          :novel-id="novelId"
          :toc-item="tocItem"
          :last-read="novel.lastReadChapterId"
        />
      </n-list-item>
    </n-list>
  </template>

  <c-drawer-right
    v-if="setting.tocCollapseInNarrowScreen && novel.toc.length > 1"
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
          <chapter-toc-item
            :provider-id="providerId"
            :novel-id="novelId"
            :toc-item="item"
            :last-read="novel.lastReadChapterId"
          />
        </div>
      </template>
    </n-virtual-list>
  </c-drawer-right>

  <comment-list
    v-if="!setting.hideCommmentWebNovel"
    :site="`web-${providerId}-${novelId}`"
    :locked="false"
  />
</template>
