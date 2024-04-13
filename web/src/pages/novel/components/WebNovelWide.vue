<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';

import { Locator } from '@/data';
import { WebNovelTocItemDto, WebNovelDto } from '@/model/WebNovel';

import { ReadableTocItem } from './common';

const props = defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelDto;
}>();

const { isSignedIn } = Locator.userDataRepository();
const setting = Locator.settingRepository().ref;

const toc = computed(() => {
  const { novel } = props;
  const novelToc = novel.toc as ReadableTocItem[];
  let order = 0;
  for (const it of novelToc) {
    it.order = it.chapterId ? order : undefined;
    if (it.chapterId) order += 1;
  }
  return novelToc;
});
</script>

<template>
  <c-layout sidebar :sidebar-width="320">
    <web-novel-metadata
      :provider-id="providerId"
      :novel-id="novelId"
      :novel="novel"
    />

    <section-header title="翻译" />
    <web-translate
      v-if="isSignedIn"
      :provider-id="providerId"
      :novel-id="novelId"
      :title-jp="novel.titleJp"
      :title-zh="novel.titleZh"
      :total="novel.toc.filter((it: WebNovelTocItemDto) => it.chapterId).length"
      v-model:jp="novel.jp"
      v-model:baidu="novel.baidu"
      v-model:youdao="novel.youdao"
      v-model:gpt="novel.gpt"
      :sakura="novel.sakura"
      :glossary="novel.glossary"
    />
    <n-p v-else>游客无法使用该功能，请先登录。</n-p>

    <CommentList :site="`web-${providerId}-${novelId}`" />

    <template #sidebar>
      <section-header title="目录">
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          @action="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </section-header>

      <n-scrollbar trigger="none" :size="24" style="flex: auto">
        <n-list style="padding-bottom: 12px">
          <n-list-item
            v-for="tocItem in setting.tocSortReverse
              ? toc.slice().reverse()
              : toc"
            :key="`${tocItem.chapterId}/${tocItem.titleJp}`"
            style="padding: 0px"
          >
            <web-novel-toc-item
              :provider-id="providerId"
              :novel-id="novelId"
              :toc-item="tocItem"
              :last-read="novel.lastReadChapterId"
              show-last-read
            />
          </n-list-item>
        </n-list>
      </n-scrollbar>
    </template>
  </c-layout>
</template>
