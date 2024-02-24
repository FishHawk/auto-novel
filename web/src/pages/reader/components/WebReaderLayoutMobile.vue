<script lang="ts" setup>
import {
  ArrowBackIosOutlined,
  ArrowForwardIosOutlined,
  TuneOutlined,
  FormatListBulletedOutlined,
  LibraryBooksOutlined,
} from '@vicons/material';
import { getScrollParent } from 'seemly';
import { ref } from 'vue';

import { WebNovelChapterDto } from '@/data/api/api_web_novel';

import { NovelInfo } from './util';

defineProps<{
  novelInfo: NovelInfo;
  chapterId: string;
  chapter: WebNovelChapterDto;
}>();

const emit = defineEmits<{
  nav: [string];
}>();

const showMenu = ref(false);
const showSettingModal = ref(false);
const showCatalogModal = ref(false);

const contentRef = ref<HTMLElement>();

const onGlobalClick = (event: MouseEvent) => {
  const scrollBy = (y: number) => {
    if (contentRef.value) {
      getScrollParent(contentRef.value)?.scrollBy({
        top: y * window.innerHeight,
        behavior: 'smooth',
      });
    }
  };
  const p = event.pageY / window.innerHeight;
  const t = 0.2;
  if (p < t) {
    scrollBy(-0.8);
  } else if (p > 1 - t) {
    scrollBy(0.8);
  } else {
    showMenu.value = true;
  }
};
</script>

<template>
  <div ref="contentRef" @click="onGlobalClick">
    <slot />
  </div>

  <reader-setting-modal v-model:show="showSettingModal" />

  <catalog-modal
    v-model:show="showCatalogModal"
    :novel-info="novelInfo"
    :chapter-id="chapterId"
    @nav="(chapterId: string) => emit('nav', chapterId)"
  />

  <n-drawer
    v-model:show="showMenu"
    :height="'auto'"
    placement="bottom"
    :auto-focus="false"
  >
    <n-flex :size="0" style="width: 100%; margin-top: 4px; margin-bottom: 4px">
      <side-button
        quaternary
        :disable="!chapter.prevId"
        text="上一话"
        :icon="ArrowBackIosOutlined"
        @click="emit('nav', chapter.prevId!!)"
        style="flex: 1"
      />
      <side-button
        v-if="novelInfo.novelUrl"
        quaternary
        tag="a"
        :href="novelInfo.novelUrl"
        text="详情"
        :icon="LibraryBooksOutlined"
        style="flex: 1"
      />
      <side-button
        quaternary
        text="目录"
        :icon="FormatListBulletedOutlined"
        @click="showCatalogModal = true"
        style="flex: 1"
      />
      <side-button
        quaternary
        text="设置"
        :icon="TuneOutlined"
        @click="showSettingModal = true"
        style="flex: 1"
      />
      <side-button
        quaternary
        :disable="!chapter.nextId"
        text="下一话"
        :icon="ArrowForwardIosOutlined"
        @click="emit('nav', chapter.nextId!!)"
        style="flex: 1"
      />
    </n-flex>
  </n-drawer>
</template>
