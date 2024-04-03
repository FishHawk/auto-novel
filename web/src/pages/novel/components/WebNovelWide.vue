<script lang="ts" setup>
import { SortOutlined } from '@vicons/material';

import { WebNovelTocItemDto } from '@/model/WebNovel';
import { Locator } from '@/data';

import { WebNovelVM } from './common';

defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelVM;
}>();

const setting = Locator.settingRepository().ref;
</script>

<template>
  <c-layout sidebar :sidebar-width="320">
    <web-novel-metadata
      :provider-id="providerId"
      :novel-id="novelId"
      :novel="novel"
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
              ? novel.toc.slice().reverse()
              : novel.toc"
            :key="tocItem.index"
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
