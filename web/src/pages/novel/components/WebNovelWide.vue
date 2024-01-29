<script lang="ts" setup>
import { SortFilled } from '@vicons/material';
import { useThemeVars } from 'naive-ui';
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
const vars = useThemeVars();

const commentListRef = ref<HTMLElement>();
const scrollToCommentList = () => {
  commentListRef.value?.scrollIntoView({ behavior: 'instant' });
};
</script>

<template>
  <n-flex :wrap="false">
    <div style="flex: auto">
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
    </div>
    <div style="flex: 0 0 350px">
      <n-flex :wrap="false" style="width: 350px; position: fixed; top: 50">
        <n-divider vertical style="height: calc(100vh - 50px); flex: 0 0 1px" />

        <n-flex vertical style="height: calc(100vh - 50px); flex: auto">
          <SectionHeader title="目录">
            <n-button @click="setting.tocSortReverse = !setting.tocSortReverse">
              <template #icon>
                <n-icon :component="SortFilled" />
              </template>
              {{ setting.tocSortReverse ? '倒序' : '正序' }}
            </n-button>
          </SectionHeader>

          <n-card
            v-if="novel.lastReadChapter"
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
          <n-divider style="margin: 4px 0" />

          <n-scrollbar trigger="none" :size="24" style="flex: auto">
            <n-list style="background-color: #0000; padding-bottom: 48px">
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
        </n-flex>
      </n-flex>
    </div>
  </n-flex>
</template>

<style scoped>
.toc:visited {
  color: color-mix(in srgb, v-bind('vars.primaryColor') 50%, red);
}
</style>
