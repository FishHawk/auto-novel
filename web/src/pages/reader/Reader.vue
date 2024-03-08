<script lang="ts" setup>
import { createReusableTemplate, onKeyStroke } from '@vueuse/core';
import { computed, ref, shallowRef, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiUser } from '@/data/api/api_user';
import { WebNovelChapterDto } from '@/data/api/api_web_novel';
import { Ok, Result } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';
import { checkIsMobile, useIsWideScreen } from '@/data/util';

import { getChapter, getNovelInfo } from './components/util';
import { TranslatorId } from '@/data/translator';
import { useReaderSettingStore } from '@/data/stores/reader_setting';

const [DefineChapterLink, ReuseChapterLink] = createReusableTemplate<{
  label: string;
  id: string | undefined;
}>();

const route = useRoute();
const router = useRouter();
const userData = useUserDataStore();
const setting = useReaderSettingStore();
const isWideScreen = useIsWideScreen(600);
const isMobile = checkIsMobile();

const currentChapterId = computed(() => route.params.chapterId as string);
const chapters = new Map<string, WebNovelChapterDto>();
const chapterResult = shallowRef<Result<WebNovelChapterDto>>();

const novelInfo = getNovelInfo(route.path, route.params);

const loadChapter = async (chapterId: string) => {
  const chapterStored = chapters.get(chapterId);
  if (chapterStored === undefined) {
    const result = await getChapter(novelInfo, chapterId);
    if (result.ok) {
      chapters.set(chapterId, result.value);
    }
    return result;
  } else {
    return Ok(chapterStored);
  }
};

const navToChapter = (targetChapterId: string) => {
  router.push(`${novelInfo.pathPrefix}/${targetChapterId}`);
};

watch(
  currentChapterId,
  async (chapterId, oldChapterId) => {
    const result = await loadChapter(chapterId);

    if (oldChapterId !== undefined) {
      window.scrollTo({
        top: 0,
        behavior: 'instant',
      });
    }

    chapterResult.value = result;
    if (result.ok) {
      document.title = result.value.titleJp;
      if (novelInfo.type === 'web' && userData.isLoggedIn) {
        ApiUser.updateReadHistoryWeb(
          novelInfo.providerId,
          novelInfo.novelId,
          chapterId
        );
      }
      if (chapters.size > 1 && result.value.nextId) {
        loadChapter(result.value.nextId);
      }
    }
  },
  { immediate: true }
);

const url = computed(() => novelInfo.getChapterUrl(currentChapterId.value));

onKeyStroke(['ArrowLeft'], (e) => {
  if (chapterResult.value?.ok) {
    if (chapterResult.value.value.prevId) {
      navToChapter(chapterResult.value.value.prevId);
      e.preventDefault();
    }
  }
});
onKeyStroke(['ArrowRight'], (e) => {
  if (chapterResult.value?.ok) {
    if (chapterResult.value.value.nextId) {
      navToChapter(chapterResult.value.value.nextId);
      e.preventDefault();
    }
  }
});

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

const showSettingModal = ref(false);
const showCatalogModal = ref(false);

onKeyStroke(['Enter'], (e) => {
  showCatalogModal.value = !showCatalogModal.value;
  e.preventDefault();
});
</script>

<template>
  <DefineChapterLink v-slot="{ id: chapterId, label }">
    <c-button
      :disabled="!chapterId"
      :lable="label"
      quaternary
      ghost
      :type="chapterId ? 'primary' : 'default'"
      @action="navToChapter(chapterId!!)"
    />
  </DefineChapterLink>

  <div class="content">
    <ResultView
      :result="chapterResult"
      :showEmpty="() => false"
      v-slot="{ value: chapter }"
    >
      <n-flex
        v-if="isWideScreen"
        align="center"
        justify="space-between"
        :wrap="false"
        style="width: 100%; margin-top: 20px"
      >
        <ReuseChapterLink :id="chapter.prevId" label="上一章" />
        <n-h4 style="text-align: center; margin: 0">
          <n-a :href="url">{{ chapter.titleJp }}</n-a>
          <br />
          <n-text depth="3">{{ chapter.titleZh }}</n-text>
        </n-h4>
        <ReuseChapterLink :id="chapter.nextId" label="下一章" />
      </n-flex>

      <div v-else style="margin-top: 20px">
        <n-h4 style="text-align: center; margin: 0 0 8px 0">
          <n-a :href="url">{{ chapter.titleJp }}</n-a>
          <br />
          <n-text depth="3">{{ chapter.titleZh }}</n-text>
        </n-h4>
        <n-flex
          align="center"
          justify="space-between"
          :wrap="false"
          style="width: 100%"
        >
          <ReuseChapterLink :id="chapter.prevId" label="上一章" />
          <ReuseChapterLink :id="chapter.nextId" label="下一章" />
        </n-flex>
      </div>

      <n-divider />

      <reader-layout-mobile
        v-if="isMobile"
        :novel-url="novelInfo.novelUrl"
        :chapter="chapter"
        @nav="navToChapter"
        @require-catalog-modal="showCatalogModal = true"
        @require-setting-modal="showSettingModal = true"
      >
        <reader-content
          :novel-info="novelInfo"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </reader-layout-mobile>
      <reader-layout-desktop
        v-else
        :novel-url="novelInfo.novelUrl"
        @require-catalog-modal="showCatalogModal = true"
        @require-setting-modal="showSettingModal = true"
      >
        <reader-content
          :novel-info="novelInfo"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </reader-layout-desktop>

      <n-divider />

      <n-flex align="center" justify="space-between" style="width: 100%">
        <ReuseChapterLink :id="chapter.prevId" label="上一章" />
        <ReuseChapterLink :id="chapter.nextId" label="下一章" />
      </n-flex>
    </ResultView>

    <reader-setting-modal v-model:show="showSettingModal" />

    <catalog-modal
      v-model:show="showCatalogModal"
      :novel-info="novelInfo"
      :chapter-id="currentChapterId"
      @nav="navToChapter"
    />
  </div>
</template>

<style scoped>
.content {
  max-width: 800px;
  margin: 0 auto;
  padding-left: v-bind("isMobile? '12px' : '24px'");
  padding-right: v-bind("isMobile? '12px' : '84px'");
  padding-bottom: 48px;
}
</style>
