<script lang="ts" setup>
import {
  ArrowBackIosOutlined,
  ArrowForwardIosOutlined,
  TuneOutlined,
  FormatListBulletedOutlined,
  LibraryBooksOutlined,
} from '@vicons/material';
import { ref } from 'vue';

import { WebNovelChapterDto } from '@/data/api/api_web_novel';

defineProps<{
  novelUrl?: string;
  chapter: WebNovelChapterDto;
}>();

const emit = defineEmits<{
  nav: [string];
  requireCatalogModal: [];
  requireSettingModal: [];
}>();

const showMenu = ref(false);

const onGlobalClick = (event: MouseEvent) => {
  const scrollBy = (y: number) => {
    window.scrollBy({
      top: y * window.innerHeight,
      behavior: 'smooth',
    });
  };
  const p = event.clientY / window.innerHeight;
  const t = 0.15;
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
  <div @click="onGlobalClick">
    <slot />
  </div>

  <n-drawer
    v-model:show="showMenu"
    :height="'auto'"
    placement="bottom"
    :auto-focus="false"
  >
    <n-flex
      :size="0"
      style="
        width: 100%;
        margin-top: 4px;
        margin-bottom: 4px;
        padding-bottom: env(safe-area-inset-bottom);
      "
    >
      <side-button
        quaternary
        :disable="!chapter.prevId"
        text="上一话"
        :icon="ArrowBackIosOutlined"
        @click="emit('nav', chapter.prevId!!)"
        style="flex: 1"
      />
      <side-button
        v-if="novelUrl"
        quaternary
        tag="a"
        :href="novelUrl"
        text="详情"
        :icon="LibraryBooksOutlined"
        style="flex: 1"
      />
      <side-button
        quaternary
        text="目录"
        :icon="FormatListBulletedOutlined"
        @click="emit('requireCatalogModal')"
        style="flex: 1"
      />
      <side-button
        quaternary
        text="设置"
        :icon="TuneOutlined"
        @click="emit('requireSettingModal')"
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
