<script lang="ts" setup>
import {
  SortOutlined,
  KeyboardArrowUpRound,
  KeyboardArrowDownRound,
} from '@vicons/material';
import { ref, computed } from 'vue';

import { Locator } from '@/data';
import { WebNovelTocItemDto, WebNovelDto } from '@/model/WebNovel';
import { ReadableTocItem } from './common';

import { useToc, useLastReadChapter } from './UseWebNovel';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const { setting } = Locator.settingRepository();

const { toc } = useToc(props.novel);
const { lastReadChapter } = useLastReadChapter(props.novel, toc);

const expandedState = ref(new Map<string, boolean>());

const displayToc = computed(() => {
  const result: ReadableTocItem[] = [];
  let currentSeparatorKey: string | null = null;
  let isCurrentSectionExpanded = true;

  for (const item of toc.value) {
    const isSeparator = item.order === undefined;
    if (isSeparator) {
      currentSeparatorKey = item.titleJp;
      if (!expandedState.value.has(currentSeparatorKey)) {
        expandedState.value.set(currentSeparatorKey, true);
      }
      isCurrentSectionExpanded =
        expandedState.value.get(currentSeparatorKey) ?? true;
      result.push(item);
    } else if (currentSeparatorKey === null || isCurrentSectionExpanded) {
      result.push(item);
    }
  }
  return result;
});

const isAnyExpanded = computed(() => {
  for (const expanded of expandedState.value.values()) {
    if (expanded) {
      return true;
    }
  }
  return false;
});

const toggleAll = () => {
  const targetState = !isAnyExpanded.value;
  for (const key of expandedState.value.keys()) {
    expandedState.value.set(key, targetState);
  }
};

const toggleSection = (separatorKey: string) => {
  expandedState.value.set(separatorKey, !expandedState.value.get(separatorKey));
};

const finalToc = computed(() => {
  const items = displayToc.value;
  // return setting.tocSortReverse ? items.slice().reverse() : items;
  return items;
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
      :locked="false"
    />

    <template #sidebar>
      <section-header title="目录">
        <c-button
          :label="isAnyExpanded ? '全部折叠' : '全部展开'"
          :icon="isAnyExpanded ? KeyboardArrowUpRound : KeyboardArrowDownRound"
          @action="toggleAll"
        />
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          @action="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </section-header>

      <n-virtual-list
        :item-size="78"
        :items="finalToc"
        item-resizable
        :default-scroll-key="lastReadChapter?.key"
        :scrollbar-props="{ trigger: 'none' }"
        style="flex: 1"
      >
        <template #default="{ item }">
          <div
            :key="
              item.order === undefined
                ? `sep-${item.titleJp}`
                : `ch-${item.chapterId}`
            "
          >
            <chapter-toc-item
              :provider-id="providerId"
              :novel-id="novelId"
              :toc-item="item"
              :last-read="novel.lastReadChapterId"
              :is-separator="item.order === undefined"
              :is-expanded="
                item.order === undefined
                  ? expandedState.get(item.titleJp)
                  : undefined
              "
              @toggle-expand="
                item.order === undefined
                  ? toggleSection(item.titleJp)
                  : () => {}
              "
            />
          </div>
        </template>
      </n-virtual-list>
    </template>
  </c-layout>
</template>
