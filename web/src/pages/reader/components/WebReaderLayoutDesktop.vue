<script lang="ts" setup>
import {
  FormatListBulletedOutlined,
  LibraryBooksOutlined,
  TuneOutlined,
} from '@vicons/material';
import { onKeyStroke } from '@vueuse/core';
import { ref } from 'vue';

import { WebNovelChapterDto } from '@/data/api/api_web_novel';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { TranslatorId } from '@/data/translator';

const props = defineProps<{
  providerId: string;
  novelId: string;
  chapterId: string;
  chapter: WebNovelChapterDto;
}>();

const emit = defineEmits<{
  nav: [string];
}>();

const setting = useReaderSettingStore();

const showSettingModal = ref(false);
const showCatalogModal = ref(false);

onKeyStroke(['1', '2', '3', '4'], (e) => {
  const translatorIds = <TranslatorId[]>['baidu', 'youdao', 'gpt', 'sakura'];
  const translatorId = translatorIds[parseInt(e.key, 10) - 1];
  if (setting.translationsMode === 'parallel') {
    if (setting.translations.includes(translatorId)) {
      setting.translations = setting.translations.filter(
        (it) => it !== translatorId
      );
    } else {
      setting.translations.push(translatorId);
    }
  } else {
    setting.translations = [translatorId];
  }
  e.preventDefault();
});

onKeyStroke(['ArrowLeft'], (e) => {
  if (props.chapter.prevId) {
    emit('nav', props.chapter.prevId);
    e.preventDefault();
  }
});
onKeyStroke(['ArrowRight'], (e) => {
  if (props.chapter.nextId) {
    emit('nav', props.chapter.nextId);
    e.preventDefault();
  }
});
onKeyStroke(['Enter'], (e) => {
  showCatalogModal.value = !showCatalogModal.value;
  e.preventDefault();
});
</script>

<template>
  <n-flex :wrap="false">
    <div style="flex: auto">
      <slot />
    </div>

    <div style="flex: 0 0 0">
      <n-flex
        size="large"
        vertical
        style="margin-left: 20px; position: fixed; bottom: 20px"
      >
        <side-button
          tag="a"
          v-if="providerId !== 'local'"
          :href="`/novel/${providerId}/${novelId}`"
          text="详情"
          :icon="LibraryBooksOutlined"
        />
        <side-button
          text="目录"
          :icon="FormatListBulletedOutlined"
          @click="showCatalogModal = true"
        />
        <side-button
          text="设置"
          :icon="TuneOutlined"
          @click="showSettingModal = true"
        />
      </n-flex>
    </div>
  </n-flex>

  <reader-setting-modal v-model:show="showSettingModal" />

  <catalog-modal
    v-model:show="showCatalogModal"
    :chapter-id="chapterId"
    @nav="(chapterId: string) => emit('nav', chapterId)"
  />
</template>
