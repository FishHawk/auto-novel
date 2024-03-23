<script lang="ts" setup>
import { createReusableTemplate, onKeyStroke } from '@vueuse/core';

import { UserRepository } from '@/data/api';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { buildWebChapterUrl } from '@/data/web/url';
import { GenericNovelId } from '@/model/Common';
import { ReaderChapter } from '@/model/Reader';
import { TranslatorId } from '@/model/Translator';
import { Ok, Result, runCatching } from '@/util/result';
import { checkIsMobile, useIsWideScreen } from '@/pages/util';
import { ReaderService } from '@/service';

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
const chapters = new Map<string, ReaderChapter>();
const chapterResult = shallowRef<Result<ReaderChapter>>();

const gnid = ((): GenericNovelId => {
  const path = route.path;
  const params = route.params;
  if (path.startsWith('/novel')) {
    const providerId = params.providerId as string;
    const novelId = params.novelId as string;
    return GenericNovelId.web(providerId, novelId);
  } else {
    const volumeId = params.novelId as string;
    return GenericNovelId.local(volumeId);
  }
})();

const novelUrl = (() => {
  if (gnid.type === 'web') {
    return `/novel/${gnid.providerId}/${gnid.novelId}`;
  }
})();

const loadChapter = async (chapterId: string) => {
  const chapterStored = chapters.get(chapterId);
  if (chapterStored === undefined) {
    const result = await runCatching(ReaderService.getChapter(gnid, chapterId));
    if (result.ok) {
      chapters.set(chapterId, result.value);
    }
    return result;
  } else {
    return Ok(chapterStored);
  }
};

const navToChapter = (targetChapterId: string) => {
  let prefix: string;
  if (gnid.type === 'web') {
    prefix = `/novel/${gnid.providerId}/${gnid.novelId}`;
  } else if (gnid.type === 'wenku') {
    throw '不支持文库';
  } else {
    prefix = `/workspace/reader/${gnid.volumeId}`;
  }
  router.push(`${prefix}/${targetChapterId}`);
};

watch(
  currentChapterId,
  async (chapterId) => {
    const result = await loadChapter(chapterId);

    chapterResult.value = result;
    if (result.ok) {
      document.title = result.value.titleJp;
      if (gnid.type === 'web' && userData.isLoggedIn) {
        UserRepository.updateReadHistoryWeb(
          gnid.providerId,
          gnid.novelId,
          chapterId
        );
      }
      // 在阅读器缓存章节大于1时，再进行预加载
      if (chapters.size > 1 && result.value.nextId) {
        loadChapter(result.value.nextId);
      }
    }
  },
  { immediate: true }
);

const chapterHref = computed(() => {
  const chapterId = currentChapterId.value;
  if (gnid.type === 'web') {
    return buildWebChapterUrl(gnid.providerId, gnid.novelId, chapterId);
  } else if (gnid.type === 'wenku') {
    throw '不支持文库';
  } else {
    return '/workspace';
  }
});

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
  <DefineChapterLink v-slot="{ id, label }">
    <c-button
      :disabled="id === undefined"
      :lable="label"
      quaternary
      :focusable="false"
      :type="id ? 'primary' : 'default'"
      @action="navToChapter(id!!)"
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
          <n-a :href="chapterHref">{{ chapter.titleJp }}</n-a>
          <br />
          <n-text depth="3">{{ chapter.titleZh }}</n-text>
        </n-h4>
        <ReuseChapterLink :id="chapter.nextId" label="下一章" />
      </n-flex>

      <div v-else style="margin-top: 20px">
        <n-h4 style="text-align: center; margin: 0 0 8px 0">
          <n-a :href="chapterHref">{{ chapter.titleJp }}</n-a>
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
        :novel-url="novelUrl"
        :chapter="chapter"
        @nav="navToChapter"
        @require-catalog-modal="showCatalogModal = true"
        @require-setting-modal="showSettingModal = true"
      >
        <reader-content
          :gnid="gnid"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </reader-layout-mobile>
      <reader-layout-desktop
        v-else
        :novel-url="novelUrl"
        @require-catalog-modal="showCatalogModal = true"
        @require-setting-modal="showSettingModal = true"
      >
        <reader-content
          :gnid="gnid"
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
      :gnid="gnid"
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
  padding-bottom: 92px;
}
</style>
