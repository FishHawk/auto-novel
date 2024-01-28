<script lang="ts" setup>
import { SortFilled } from '@vicons/material';
import { useThemeVars } from 'naive-ui';

import { WebNovelTocItemDto } from '@/data/api/api_web_novel';
import { useSettingStore } from '@/data/stores/setting';

import { WebNovelVM } from './common';

defineProps<{
  providerId: string;
  novelId: string;
  novel: WebNovelVM;
}>();

const setting = useSettingStore();
const vars = useThemeVars();
</script>

<template>
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

  <section>
    <SectionHeader title="目录">
      <n-button @click="setting.tocSortReverse = !setting.tocSortReverse">
        <template #icon>
          <n-icon :component="SortFilled" />
        </template>
        {{ setting.tocSortReverse ? '倒序' : '正序' }}
      </n-button>
    </SectionHeader>

    <n-list style="background-color: #0000">
      <n-card
        v-if="novel.lastReadChapter"
        :bordered="false"
        embedded
        style="margin-bottom: 8px"
        content-style="padding: 6px 0px 0px;"
      >
        <b style="padding-left: 6px">上次读到:</b>
        <web-novel-toc-item
          :provider-id="providerId"
          :novel-id="novelId"
          :toc-item="novel.lastReadChapter"
        />
      </n-card>
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
  </section>

  <CommentList :site="`web-${providerId}-${novelId}`" />
</template>

<style scoped>
.toc:visited {
  color: color-mix(in srgb, v-bind('vars.primaryColor') 50%, red);
}
</style>
