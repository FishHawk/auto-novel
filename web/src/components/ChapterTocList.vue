<script lang="ts" setup>
import { NCollapse, NCollapseItem, NVirtualList } from 'naive-ui';
import ChapterTocItem from '@/components/ChapterTocItem.vue';
import type { ReadableTocItem } from '@/pages/novel/components/common';
import { computed, onMounted, nextTick, watch } from 'vue';

interface TocSection {
  separator: ReadableTocItem | null;
  chapters: ReadableTocItem[];
}

const props = defineProps<{
  tocSections: TocSection[];
  expandedNames: string[];
  lastReadChapterId?: string;
  providerId: string;
  novelId: string;
  sortReverse: boolean;
  mode: {
    narrow: boolean;
    catalog: boolean;
    collapse: boolean;
  };
}>();

const emit = defineEmits<{
  'update:expandedNames': [string[]];
  itemClick: [ReadableTocItem];
}>();

const handleItemClick = (item: ReadableTocItem) => {
  if (item.chapterId !== undefined) {
    emit('itemClick', item);
  }
};

const sortedChapters = (chapters: ReadableTocItem[]) => {
  return props.sortReverse ? chapters.slice().reverse() : chapters;
};

const sortedSections = computed(() => {
  const sections = props.tocSections;
  return props.sortReverse ? sections.slice().reverse() : sections;
});

const noNeedScroll = computed(() => {
  return props.mode.narrow && !props.mode.catalog && !props.mode.collapse;
});

const scrollToLastRead = async () => {
  if (noNeedScroll.value) return;
  if (!props.lastReadChapterId) return;

  await nextTick();

  const elementId = `chapterTocItem-${props.lastReadChapterId}`;
  let element: HTMLElement | null = null;

  for (let i = 0; i < 5; i++) {
    // console.debug('attempting to scroll to last read chapter', elementId, 'for attempt', i);
    element = document.getElementById(elementId);
    if (element) {
      element.scrollIntoView({ behavior: 'instant', block: 'center' });
      break;
    }
    await new Promise((resolve) => setTimeout(resolve, 100 + i * 50));
  }
};

onMounted(() => {
  scrollToLastRead();
});
</script>

<template>
  <n-collapse
    :expanded-names="expandedNames"
    @update:expanded-names="$emit('update:expandedNames', $event)"
    arrow-placement="right"
  >
    <template v-for="(section, index) in sortedSections" :key="index">
      <n-collapse-item
        v-if="section.separator"
        :name="section.separator.titleJp"
        display-directive="show"
      >
        <template #header>
          <chapter-toc-item
            :provider-id="providerId"
            :novel-id="novelId"
            :toc-item="section.separator"
            :is-separator="true"
            style="width: 100%"
          />
        </template>
        <n-virtual-list
          v-if="section.chapters.length > 0"
          :items="sortedChapters(section.chapters)"
          :item-size="75.2"
          :scrollbar-props="{ trigger: 'none' }"
          item-resizable
        >
          <template #default="{ item: chapter }">
            <div :key="`ch-${chapter.chapterId}`">
              <chapter-toc-item
                :provider-id="providerId"
                :novel-id="novelId"
                :toc-item="chapter"
                :last-read="lastReadChapterId"
                :is-separator="false"
                @click="handleItemClick(chapter)"
              />
            </div>
          </template>
        </n-virtual-list>
      </n-collapse-item>
      <n-virtual-list
        v-else-if="section.chapters.length > 0"
        :items="sortedChapters(section.chapters)"
        :item-size="75.2"
        :scrollbar-props="{ trigger: 'none' }"
        item-resizable
      >
        <template #default="{ item: chapter }">
          <div :key="`ch-${chapter.chapterId}`">
            <chapter-toc-item
              :provider-id="providerId"
              :novel-id="novelId"
              :toc-item="chapter"
              :last-read="lastReadChapterId"
              :is-separator="false"
              @click="handleItemClick(chapter)"
            />
          </div>
        </template>
      </n-virtual-list>
    </template>
  </n-collapse>
</template>
