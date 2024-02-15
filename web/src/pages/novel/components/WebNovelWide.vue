<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';
import { ref } from 'vue';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';
import { useSettingStore } from '@/data/stores/setting';

import { WebNovelVM } from './common';

defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelVM;
}>();

const setting = useSettingStore();

const commentListRef = ref<HTMLElement>();
const scrollToCommentList = () => {
  commentListRef.value?.scrollIntoView({ behavior: 'instant' });
};
</script>

<template>
  <c-layout sidebar :sidebar-width="320">
    <web-novel-metadata
      :provider-id="providerId"
      :novel-id="novelId"
      :novel="novel"
      @comment-click="scrollToCommentList()"
    />

    <section-header title="翻译" />
    <WebTranslate
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

    <div ref="commentListRef"></div>
    <CommentList :site="`web-${providerId}-${novelId}`" />

    <template #sidebar>
      <section-header title="目录">
        <c-button
          :label="setting.tocSortReverse ? '倒序' : '正序'"
          :icon="SortOutlined"
          @click="setting.tocSortReverse = !setting.tocSortReverse"
        />
      </section-header>

      <template v-if="novel.lastReadChapter">
        <n-card
          :bordered="false"
          embedded
          content-style="padding: 6px 0px 0px;"
        >
          <b style="padding-left: 6px">上次读到:</b>
          <web-novel-toc-item
            :provider-id="providerId"
            :novel-id="novelId"
            :toc-item="novel.lastReadChapter"
          />
        </n-card>

        <n-divider style="margin: 16px 0" />
      </template>

      <n-scrollbar trigger="none" :size="24" style="flex: auto">
        <n-list style="background-color: #0000; padding-bottom: 12px">
          <n-list-item
            v-for="tocItem in setting.tocSortReverse
              ? novel.toc.slice().reverse()
              : novel.toc"
            :key="tocItem.index"
            style="padding: 0px"
          >
            <web-novel-toc-item
              :provider-id="providerId"
              :novel-id="novelId"
              :toc-item="tocItem"
            />
          </n-list-item>
        </n-list>
      </n-scrollbar>
    </template>
  </c-layout>
</template>
