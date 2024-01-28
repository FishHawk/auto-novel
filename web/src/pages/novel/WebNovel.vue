<script lang="ts" setup>
import { ref } from 'vue';
import { useRoute } from 'vue-router';

import { ApiWebNovel } from '@/data/api/api_web_novel';
import { Result, mapOk } from '@/data/result';
import { useIsWideScreen } from '@/data/util';

import { ReadableTocItem, WebNovelVM } from './components/common';

const isWideScreen = useIsWideScreen(750);

const route = useRoute();
const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;

const novelResult = ref<Result<WebNovelVM>>();

const getNovel = async () => {
  novelResult.value = undefined;
  const result = await ApiWebNovel.getNovel(providerId, novelId);
  const newResult = mapOk(result, (novel) => {
    const novelToc = novel.toc as ReadableTocItem[];
    let order = 0;
    let index = 0;
    for (const it of novelToc) {
      it.index = index;
      it.order = it.chapterId ? order : undefined;
      if (it.chapterId) order += 1;
      index += 1;
    }

    novel.toc = [];
    return <WebNovelVM>{
      ...novel,
      toc: novelToc,
      lastReadChapter: novelToc.find(
        (it) => it.chapterId === novel.lastReadChapterId
      ),
    };
  });
  novelResult.value = newResult;
  if (result.ok) {
    document.title = result.value.titleJp;
  }
};
getNovel();
</script>

<template>
  <div class="layout-content">
    <ResultView
      :result="novelResult"
      :showEmpty="() => false"
      v-slot="{ value: novel }"
    >
      <web-novel-wide
        v-if="isWideScreen"
        :provider-id="providerId"
        :novel-id="novelId"
        :novel="novel"
      />
      <web-novel-narrow
        v-else
        :provider-id="providerId"
        :novel-id="novelId"
        :novel="novel"
      />
    </ResultView>
  </div>
</template>
