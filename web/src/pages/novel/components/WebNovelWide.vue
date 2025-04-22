<script lang="ts" setup>
import {
  SortOutlined,
  KeyboardArrowUpRound,
  KeyboardArrowDownRound,
} from '@vicons/material';
import { ref, computed, watch } from 'vue';

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

watch(
  toc,
  (newToc) => {
    for (const item of newToc) {
      if (item.order === undefined) {
        const key = item.titleJp;
        if (!expandedState.value.has(key)) {
          expandedState.value.set(key, true);
        }
      }
    }
  },
  { immediate: true, deep: true },
);

const hasSeparators = computed(() => {
  return toc.value.some((item) => item.order === undefined);
});

const isAnyExpanded = computed(() => {
  if (!hasSeparators.value) {
    return false;
  }
  for (const item of toc.value) {
    if (item.order === undefined) {
      const key = item.titleJp;
      if (expandedState.value.get(key)) {
        return true;
      }
    }
  }
  return false;
});

const toggleAll = () => {
  const targetState = !isAnyExpanded.value;
  for (const item of toc.value) {
    if (item.order === undefined) {
      expandedState.value.set(item.titleJp, targetState);
    }
  }
};

const toggleSection = (separatorKey: string) => {
  expandedState.value.set(separatorKey, !expandedState.value.get(separatorKey));
};

interface TocSection {
  separator: ReadableTocItem | null;
  chapters: ReadableTocItem[];
}

const finalToc = computed(() => {
  const sections: TocSection[] = [];
  let currentSection: TocSection = { separator: null, chapters: [] };

  for (const item of toc.value) {
    if (item.order === undefined) {
      if (currentSection.separator || currentSection.chapters.length > 0) {
        sections.push(currentSection);
      }
      const key = item.titleJp;
      if (!expandedState.value.has(key)) {
        expandedState.value.set(key, true);
      }
      currentSection = { separator: item, chapters: [] };
    } else {
      currentSection.chapters.push(item);
    }
  }
  sections.push(currentSection);

  const filteredSections = sections.map((section) => {
    if (section.separator) {
      const isExpanded =
        expandedState.value.get(section.separator.titleJp) ?? true;
      return {
        ...section,
        chapters: isExpanded ? section.chapters : [],
      };
    }
    return section;
  });

  let result: ReadableTocItem[] = [];
  if (!setting.value.tocSortReverse) {
    filteredSections.forEach((section) => {
      if (section.separator) {
        result.push(section.separator);
      }
      result.push(...section.chapters);
    });
  } else {
    const reversedSections = filteredSections.slice().reverse();
    reversedSections.forEach((section) => {
      if (section.separator) {
        result.push(section.separator);
      }
      result.push(...section.chapters.reverse());
    });
  }

  return result;
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
          v-if="hasSeparators"
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
