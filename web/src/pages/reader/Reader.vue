<script lang="ts" setup>
import { createReusableTemplate, onKeyDown } from '@vueuse/core';

import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { TranslatorId } from '@/model/Translator';
import { Result } from '@/util/result';
import { WebUtil } from '@/util/web';

import { checkIsMobile, useIsWideScreen } from '@/pages/util';
import { ReaderChapter, useReaderStore } from './ReaderStore';

const [DefineChapterLink, ReuseChapterLink] = createReusableTemplate<{
  label: string;
  id: string | undefined;
}>();

const route = useRoute();
const router = useRouter();
const isWideScreen = useIsWideScreen(600);
const isMobile = checkIsMobile();

const { isSignedIn } = Locator.authRepository();
const { setting } = Locator.readerSettingRepository();

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

const store = useReaderStore(gnid);

const targetChapterId = ref('');
const currentChapterId = ref('');
const chapterResult = shallowRef<Result<ReaderChapter>>();
const loadingBar = useLoadingBar();

const novelUrl = (() => {
  if (gnid.type === 'web') {
    return `/novel/${gnid.providerId}/${gnid.novelId}`;
  }
})();

const navToChapter = async (chapterId: string) => {
  targetChapterId.value = chapterId;

  const { type, promiseOrValue } = store.loadChapter(chapterId);

  if (type === 'async') {
    loadingBar.start();
  }

  const result = await promiseOrValue;
  if (chapterId !== targetChapterId.value) {
    return;
  }

  if (result.ok) {
    loadingBar.finish();
  } else {
    loadingBar.error();
  }

  chapterResult.value = result;

  if (currentChapterId.value !== chapterId) {
    if (result.ok) {
      document.title = result.value.titleJp;
      if (gnid.type === 'web' && isSignedIn) {
        Locator.readHistoryRepository().updateReadHistoryWeb(
          gnid.providerId,
          gnid.novelId,
          chapterId,
        );
      } else if (gnid.type === 'local') {
        Locator.localVolumeRepository().then((it) =>
          it.updateReadAt(gnid.volumeId),
        );
      }
      if (result.value.nextId) {
        store.preloadChapter(result.value.nextId);
      }
    }

    let prefix: string;
    if (gnid.type === 'web') {
      prefix = `/novel/${gnid.providerId}/${gnid.novelId}`;
    } else if (gnid.type === 'wenku') {
      throw '不支持文库';
    } else {
      prefix = `/workspace/reader/${encodeURIComponent(gnid.volumeId)}`;
    }
    currentChapterId.value = chapterId;
    router.push(`${prefix}/${chapterId}`);
  }
};

watch(
  route,
  (route) => {
    const urlChapterId = route.params.chapterId as string;
    if (urlChapterId !== targetChapterId.value) {
      chapterResult.value = undefined;
      navToChapter(urlChapterId);
    }
  },
  {
    immediate: true,
  },
);

const chapterHref = computed(() => {
  const chapterId = currentChapterId.value;
  if (gnid.type === 'web') {
    return WebUtil.buildChapterUrl(gnid.providerId, gnid.novelId, chapterId);
  } else if (gnid.type === 'wenku') {
    throw '不支持文库';
  } else {
    return '/workspace';
  }
});

onKeyDown(['ArrowLeft'], (e) => {
  if (e.altKey || e.ctrlKey || e.shiftKey || e.metaKey) {
    return;
  }
  if (chapterResult.value?.ok && chapterResult.value.value.prevId) {
    navToChapter(chapterResult.value.value.prevId);
    e.preventDefault();
  }
});
onKeyDown(['ArrowRight'], (e) => {
  if (e.altKey || e.ctrlKey || e.shiftKey || e.metaKey) {
    return;
  }
  if (chapterResult.value?.ok && chapterResult.value.value.nextId) {
    navToChapter(chapterResult.value.value.nextId);
    e.preventDefault();
  }
});

onKeyDown(['1', '2', '3', '4'], (e) => {
  const setting = Locator.readerSettingRepository().setting.value;

  const translatorIds = <TranslatorId[]>['baidu', 'youdao', 'gpt', 'sakura'];
  const translatorId = translatorIds[parseInt(e.key, 10) - 1];
  if (setting.translationsMode === 'parallel') {
    if (setting.translations.includes(translatorId)) {
      setting.translations = setting.translations.filter(
        (it) => it !== translatorId,
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

onKeyDown(['Enter'], (e) => {
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
      @action="
        () => {
          navToChapter(id!!);
        }
      "
    />
  </DefineChapterLink>

  <div class="content">
    <c-result :result="chapterResult" v-slot="{ value: chapter }">
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
    </c-result>

    <reader-setting-modal v-model:show="showSettingModal" />

    <catalog-modal
      v-model:show="showCatalogModal"
      :gnid="gnid"
      :chapter-id="currentChapterId"
    />
  </div>
</template>

<style scoped>
.content {
  max-width: v-bind('`${setting.pageWidth}px`');
  margin: 0 auto;
  padding-left: v-bind("isMobile? '12px' : '24px'");
  padding-right: v-bind("isMobile? '12px' : '84px'");
  padding-bottom: 92px;
}
</style>
