<script lang="ts" setup>
import { createReusableTemplate } from '@vueuse/core';
import { getScrollParent } from 'seemly';
import { computed, ref, shallowRef, watch } from 'vue';
import { useRoute } from 'vue-router';

import { ApiUser } from '@/data/api/api_user';
import { WebNovelChapterDto } from '@/data/api/api_web_novel';
import { Ok, Result } from '@/data/result';
import { useUserDataStore } from '@/data/stores/user_data';
import { checkIsMobile, useIsWideScreen } from '@/data/util';

import { getChapter, getNovelInfo } from './components/util';

const [DefineChapterLink, ReuseChapterLink] = createReusableTemplate<{
  label: string;
  id: string | undefined;
}>();

const isWideScreen = useIsWideScreen(600);
const userData = useUserDataStore();
const route = useRoute();
const isMobile = checkIsMobile();

const currentChapterId = ref(route.params.chapterId as string);
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

const navToChapter = async (targetChapterId: string) => {
  currentChapterId.value = targetChapterId;
};

const placeholderRef = ref<HTMLElement>();
watch(
  currentChapterId,
  async (chapterId, oldChapterId) => {
    const result = await loadChapter(chapterId);
    if (placeholderRef.value) {
      getScrollParent(placeholderRef.value)?.scrollTo({
        top: 0,
        behavior: 'instant',
      });
    }

    if (oldChapterId !== chapterId) {
      window.history.pushState(
        {},
        document.title,
        `${novelInfo.pathPrefix}/${chapterId}`
      );
    }
    if (result.ok) {
      document.title = result.value.titleJp;
      chapterResult.value = result;
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
</script>

<template>
  <DefineChapterLink v-slot="{ id: chapterId, label }">
    <c-button
      :disabled="!chapterId"
      :lable="label"
      quaternary
      ghost
      :type="chapterId ? 'primary' : 'default'"
      @click="navToChapter(chapterId!!)"
    />
  </DefineChapterLink>

  <div ref="placeholderRef" class="content">
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
        <n-h4 style="text-align: center; margin: 0">
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

      <web-reader-layout-mobile
        v-if="isMobile"
        :novel-info="novelInfo"
        :chapter-id="currentChapterId"
        :chapter="chapter"
        @nav="navToChapter"
      >
        <web-reader-content
          :novel-info="novelInfo"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </web-reader-layout-mobile>
      <web-reader-layout-desktop
        v-else
        :novel-info="novelInfo"
        :chapter-id="currentChapterId"
        :chapter="chapter"
        @nav="navToChapter"
      >
        <web-reader-content
          :novel-info="novelInfo"
          :chapter-id="currentChapterId"
          :chapter="chapter"
        />
      </web-reader-layout-desktop>

      <n-divider />

      <n-flex align="center" justify="space-between" style="width: 100%">
        <ReuseChapterLink :id="chapter.prevId" label="上一章" />
        <ReuseChapterLink :id="chapter.nextId" label="下一章" />
      </n-flex>
    </ResultView>
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
