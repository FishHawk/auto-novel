<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WebNovelTocItemDto, WebNovelDto } from '@/model/WebNovel';

import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const { setting } = Locator.settingRepository();

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
</script>

<template>
  <c-layout sidebar :sidebar-width="320">
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

    <comment-list
      v-if="!setting.hideCommmentWebNovel"
      :site="`web-${providerId}-${novelId}`"
    />

    <template #sidebar>
      <section-header title="目录">
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          @action="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </section-header>

      <n-virtual-list
        :item-size="78"
        :items="setting.tocSortReverse ? toc.slice().reverse() : toc"
        item-resizable
        :default-scroll-key="lastReadChapter?.key"
        :scrollbar-props="{ trigger: 'none' }"
        style="flex: 1"
      >
        <template #default="{ item }">
          <div
            :key="
              item.chapterId === undefined ? `/${item.titleJp}` : item.chapterId
            "
          >
            <web-novel-toc-item
              :provider-id="providerId"
              :novel-id="novelId"
              :toc-item="item"
              :last-read="novel.lastReadChapterId"
            />
          </div>
        </template>
      </n-virtual-list>
    </template>
  </c-layout>
</template>
