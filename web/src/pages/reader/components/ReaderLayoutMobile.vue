<script lang="ts" setup>
import {
  ArrowBackIosOutlined,
  ArrowForwardIosOutlined,
  FormatListBulletedOutlined,
  LibraryBooksOutlined,
  TuneOutlined,
} from '@vicons/material';

import { WebNovelChapterDto } from '@/model/WebNovel';

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
      <div style="flex: 1 1 0px">
        <side-button
          quaternary
          :disabled="!chapter.prevId"
          text="上一章"
          :icon="ArrowBackIosOutlined"
          @click="emit('nav', chapter.prevId!!)"
          style="width: 100%"
        />
      </div>
      <router-link v-if="novelUrl" :to="novelUrl" style="flex: 1">
        <side-button
          quaternary
          text="详情"
          :icon="LibraryBooksOutlined"
          style="width: 100%"
        />
      </router-link>
      <div style="flex: 1 1 0px">
        <side-button
          quaternary
          text="目录"
          :icon="FormatListBulletedOutlined"
          @click="emit('requireCatalogModal')"
          style="width: 100%"
        />
      </div>
      <div style="flex: 1 1 0px">
        <side-button
          quaternary
          text="设置"
          :icon="TuneOutlined"
          @click="emit('requireSettingModal')"
          style="width: 100%"
        />
      </div>
      <div style="flex: 1 1 0px">
        <side-button
          quaternary
          :disabled="!chapter.nextId"
          text="下一章"
          :icon="ArrowForwardIosOutlined"
          @click="emit('nav', chapter.nextId!!)"
          style="width: 100%"
        />
      </div>
    </n-flex>
  </n-drawer>
</template>
