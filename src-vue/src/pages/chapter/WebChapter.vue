<script lang="ts" setup>
import { onMounted, ref, shallowRef } from 'vue';
import { useRoute } from 'vue-router';
import { NConfigProvider, lightTheme, darkTheme } from 'naive-ui';

import { ResultState } from '@/data/api/result';
import { ApiUser } from '@/data/api/api_user';
import { ApiWebNovel, WebNovelChapterDto } from '@/data/api/api_web_novel';
import { useAuthInfoStore } from '@/data/stores/authInfo';
import { useReaderSettingStore } from '@/data/stores/readerSetting';
import { buildChapterUrl } from '@/data/provider';

const authInfoStore = useAuthInfoStore();

const setting = useReaderSettingStore();

const route = useRoute();
const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;
const chapterId = route.params.chapterId as string;
const url = buildChapterUrl(providerId, novelId, chapterId);

const showModal = ref(false);

const chapter = shallowRef<ResultState<WebNovelChapterDto>>();
async function getChapter() {
  const result = await ApiWebNovel.getChapter(providerId, novelId, chapterId);
  chapter.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
    if (authInfoStore.token) {
      ApiUser.putReadHistoryWebNovel(
        providerId,
        novelId,
        chapterId,
        authInfoStore.token
      );
    }
  }
}
getChapter();

type Paragraph =
  | { text: string; secondary: boolean }
  | { imageUrl: string }
  | null;

function getTextList(chapter: WebNovelChapterDto): Paragraph[] {
  const merged: Paragraph[] = [];
  const styles: { paragraphs: string[]; secondary: boolean }[] = [];

  if (setting.mode === 'jp') {
    styles.push({ paragraphs: chapter.paragraphs, secondary: false });
  } else {
    if (setting.mode === 'mix-reverse') {
      styles.push({ paragraphs: chapter.paragraphs, secondary: true });
    }

    if (setting.translation === 'youdao') {
      // 有道优先
      if (chapter.youdaoParagraphs) {
        styles.push({ paragraphs: chapter.youdaoParagraphs, secondary: false });
      } else if (chapter.baiduParagraphs) {
        merged.push({ text: '有道翻译不存在，使用百度翻译', secondary: true });
        styles.push({ paragraphs: chapter.baiduParagraphs, secondary: false });
      } else {
        merged.push({ text: '无中文翻译', secondary: false });
        return merged;
      }
    } else if (setting.translation === 'baidu') {
      // 百度优先
      if (chapter.baiduParagraphs) {
        styles.push({ paragraphs: chapter.baiduParagraphs, secondary: false });
      } else if (chapter.youdaoParagraphs) {
        merged.push({ text: '百度翻译不存在，使用有道翻译', secondary: true });
        styles.push({ paragraphs: chapter.youdaoParagraphs, secondary: false });
      } else {
        merged.push({ text: '无中文翻译', secondary: false });
        return merged;
      }
    } else if (setting.translation === 'youdao/baidu') {
      if (chapter.youdaoParagraphs && chapter.baiduParagraphs) {
        styles.push({ paragraphs: chapter.youdaoParagraphs, secondary: false });
        styles.push({ paragraphs: chapter.baiduParagraphs, secondary: false });
      } else {
        if (!chapter.youdaoParagraphs) {
          merged.push({ text: '有道翻译不存在', secondary: false });
        }
        if (!chapter.baiduParagraphs) {
          merged.push({ text: '百度翻译不存在', secondary: false });
        }
        return merged;
      }
    }

    if (setting.mode === 'mix') {
      styles.push({ paragraphs: chapter.paragraphs, secondary: true });
    }
  }

  for (let i = 0; i < chapter.paragraphs.length; i++) {
    if (chapter.paragraphs[i].trim().length === 0) {
      merged.push(null);
    } else if (chapter.paragraphs[i].startsWith('<图片>')) {
      merged.push({ imageUrl: chapter.paragraphs[i].slice(4) });
    } else {
      for (const style of styles) {
        merged.push({ text: style.paragraphs[i], secondary: style.secondary });
      }
    }
  }
  return merged;
}
</script>

<template>
  <n-config-provider
    :theme="setting.theme.isDark ? darkTheme : lightTheme"
    :theme-overrides="{
      common: { bodyColor: setting.theme.bodyColor },
      Pagination: { itemColorDisabled: '#00000' },
    }"
  >
    <n-global-style />

    <ReaderSettingDialog v-model:show="showModal" />

    <div class="content" v-if="chapter?.ok">
      <n-h2 style="text-align: center; width: 100%">
        <n-a :href="url" target="_blank">{{ chapter.value.titleJp }}</n-a>
        <br />
        <span style="color: gray">{{ chapter.value.titleZh }}</span>
      </n-h2>

      <n-space align="center" justify="space-between" style="width: 100%">
        <n-a
          v-if="chapter.value.prevId"
          :href="`/novel/${providerId}/${novelId}/${chapter.value.prevId}`"
          >上一章</n-a
        >
        <n-text v-else style="color: grey">上一章</n-text>

        <n-a :href="`/novel/${providerId}/${novelId}`">目录</n-a>
        <n-a @click="showModal = true">设置</n-a>

        <n-a
          v-if="chapter.value.nextId"
          :href="`/novel/${providerId}/${novelId}/${chapter.value.nextId}`"
          >下一章</n-a
        >
        <n-text v-else style="color: grey">下一章</n-text>
      </n-space>

      <n-divider />

      <div id="chapter-content">
        <template v-for="p in getTextList(chapter.value)">
          <n-p v-if="p && 'text' in p" :class="{ secondary: p.secondary }">{{
            p.text
          }}</n-p>
          <br v-else-if="!p" />
          <img v-else :src="p.imageUrl" :alt="p.imageUrl" style="width: 100%" />
        </template>
      </div>

      <n-divider />

      <n-space align="center" justify="space-between" style="width: 100%">
        <n-a
          v-if="chapter.value.prevId"
          :href="`/novel/${providerId}/${novelId}/${chapter.value.prevId}`"
          >上一章</n-a
        >
        <n-text v-else style="color: grey">上一章</n-text>

        <n-a
          v-if="chapter.value.nextId"
          :href="`/novel/${providerId}/${novelId}/${chapter.value.nextId}`"
          >下一章</n-a
        >
        <n-text v-else style="color: grey">下一章</n-text>
      </n-space>
    </div>

    <div v-if="chapter && !chapter.ok">
      <n-result
        status="error"
        title="加载错误"
        :description="chapter.error.message"
      />
    </div>
  </n-config-provider>
</template>

<style scoped>
#chapter-content p {
  word-wrap: break-word;
  font-size: v-bind('setting.fontSize');
}
#chapter-content .secondary {
  opacity: v-bind('setting.mixJpOpacity');
}
.content {
  width: 800px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 48px;
}
@media only screen and (max-width: 600px) {
  .content {
    width: auto;
    padding-left: 10px;
    padding-right: 10px;
    padding-bottom: 48px;
  }
}
</style>
