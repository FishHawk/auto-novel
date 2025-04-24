<script lang="ts" setup>
import {
  SortOutlined,
  KeyboardArrowUpRound,
  KeyboardArrowDownRound,
} from '@vicons/material';
import { computed, ref } from 'vue';

import { Locator } from '@/data';
import { WebNovelTocItemDto, WebNovelDto } from '@/model/WebNovel';
import ChapterTocList from '@/components/ChapterTocList.vue';

import { useToc, useLastReadChapter } from './UseWebNovel';
import { useTocExpansion } from './UseTocExpansion';

import { NScrollbar } from 'naive-ui';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const { setting } = Locator.settingRepository();
const sortReverse = computed(() => setting.value.tocSortReverse);

const { toc } = useToc(props.novel);
const { lastReadChapter } = useLastReadChapter(props.novel, toc);

const defaultTocExpanded = computed(() => setting.value.tocExpandAll);

const { expandedNames, hasSeparators, isAnyExpanded, toggleAll, tocSections } =
  useTocExpansion(
    toc,
    defaultTocExpanded,
    computed(() => props.novel.lastReadChapterId),
  );
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
      :locked="false"
    />

    <template #sidebar>
      <section-header title="目录">
        <c-button
          v-if="hasSeparators"
          :label="isAnyExpanded ? '折叠' : '展开'"
          :icon="isAnyExpanded ? KeyboardArrowUpRound : KeyboardArrowDownRound"
          quaternary
          size="small"
          :round="false"
          @action="toggleAll"
          style="margin-right: 8px"
        />
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          quaternary
          size="small"
          :round="false"
          @action="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </section-header>

      <n-scrollbar style="flex: 1; min-height: 0; padding: 0 16px 0 0">
        <chapter-toc-list
          :toc-sections="tocSections"
          v-model:expanded-names="expandedNames"
          :last-read-chapter-id="novel.lastReadChapterId"
          :provider-id="providerId"
          :novel-id="novelId"
          :sort-reverse="sortReverse"
          :mode="{
            narrow: false,
            catalog: false,
            collapse: false,
          }"
        />
      </n-scrollbar>
    </template>
  </c-layout>
</template>
