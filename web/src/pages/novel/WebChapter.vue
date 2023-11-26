<script lang="ts" setup>
import { createReusableTemplate, onKeyStroke } from '@vueuse/core';
import { NConfigProvider, darkTheme, lightTheme, useMessage } from 'naive-ui';
import { ref, shallowRef } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { ApiUser } from '@/data/api/api_user';
import { ApiWebNovel, WebNovelChapterDto } from '@/data/api/api_web_novel';
import { ResultState } from '@/data/result';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { useUserDataStore } from '@/data/stores/user_data';
import { TranslatorId } from '@/data/translator/translator';
import { buildWebChapterUrl } from '@/data/util_web';
import { ApiSakura } from '@/data/api/api_sakura';

const [DefineChapterLink, ReuseChapterLink] = createReusableTemplate<{
  id: string | undefined;
}>();

const userData = useUserDataStore();
const setting = useReaderSettingStore();
const route = useRoute();
const router = useRouter();
const message = useMessage();

const providerId = route.params.providerId as string;
const novelId = route.params.novelId as string;
const chapterId = route.params.chapterId as string;

const showModal = ref(false);

const chapterResult = shallowRef<ResultState<WebNovelChapterDto>>();
async function getChapter() {
  const result = await ApiWebNovel.getChapter(providerId, novelId, chapterId);
  chapterResult.value = result;
  if (result.ok) {
    document.title = result.value.titleJp;
    if (userData.isLoggedIn) {
      ApiUser.updateReadHistoryWeb(providerId, novelId, chapterId);
    }
  }
}
getChapter();

onKeyStroke(['ArrowLeft'], (e) => {
  if (chapterResult.value?.ok && chapterResult.value.value.prevId) {
    const prevId = chapterResult.value.value.prevId;
    const prevUrl = `/novel/${providerId}/${novelId}/${prevId}`;
    router.push(prevUrl);
    e.preventDefault();
  }
});
onKeyStroke(['ArrowRight'], (e) => {
  if (chapterResult.value?.ok && chapterResult.value.value.nextId) {
    const nextId = chapterResult.value.value.nextId;
    const nextUrl = `/novel/${providerId}/${novelId}/${nextId}`;
    router.push(nextUrl);
    e.preventDefault();
  }
});

type Paragraph =
  | { text: string; secondary: boolean; popover?: string }
  | { imageUrl: string }
  | null;

function getTextList(chapter: WebNovelChapterDto): Paragraph[] {
  const merged: Paragraph[] = [];
  const styles: {
    paragraphs: string[];
    secondary: boolean;
    popover?: boolean;
  }[] = [];

  if (setting.mode === 'jp') {
    styles.push({ paragraphs: chapter.paragraphs, secondary: false });
  } else {
    if (setting.mode === 'mix-reverse') {
      styles.push({ paragraphs: chapter.paragraphs, secondary: true });
    }

    function paragraphsWithLabel(
      t: TranslatorId
    ): [string, string[] | undefined] {
      if (t === 'youdao') {
        return ['有道', chapter.youdaoParagraphs];
      } else if (t === 'baidu') {
        return ['百度', chapter.baiduParagraphs];
      } else if (t === 'gpt') {
        return ['GPT3', chapter.gptParagraphs];
      } else {
        return ['Sakura', chapter.sakuraParagraphs];
      }
    }
    if (setting.translationsMode === 'priority') {
      let hasAnyTranslation = false;
      for (const t of setting.translations) {
        const [label, paragraphs] = paragraphsWithLabel(t);
        if (paragraphs) {
          hasAnyTranslation = true;
          styles.push({
            paragraphs,
            secondary: false,
            popover: t === 'sakura',
          });
          break;
        } else {
          merged.push({ text: label + '翻译不存在', secondary: true });
        }
      }
      if (!hasAnyTranslation) {
        return merged;
      }
    } else {
      for (const t of setting.translations) {
        const [label, paragraphs] = paragraphsWithLabel(t);
        if (paragraphs) {
          styles.push({
            paragraphs,
            secondary: false,
            popover: t === 'sakura',
          });
        } else {
          merged.push({ text: label + '翻译不存在', secondary: true });
        }
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
        merged.push({
          text: style.paragraphs[i],
          secondary: style.secondary,
          popover: style.popover === true ? chapter.paragraphs[i] : undefined,
        });
      }
    }
  }
  return merged;
}

const createWebIncorrectCase = async (jp: string, zh: string) => {
  const result = await ApiSakura.createWebIncorrectCase({
    providerId,
    novelId,
    chapterId,
    jp,
    zh,
  });
  if (result.ok) {
    message.info('提交成功');
  } else {
    message.error('提交失败:' + result.error.message);
  }
};
</script>

<template>
  <DefineChapterLink v-slot="{ $slots, id: chapterId }">
    <RouterNA
      v-if="chapterId"
      :to="`/novel/${providerId}/${novelId}/${chapterId}`"
    >
      <n-button quaternary type="primary">
        <component :is="$slots.default!" />
      </n-button>
    </RouterNA>
    <n-button v-else disabled quaternary>
      <component :is="$slots.default!" />
    </n-button>
  </DefineChapterLink>

  <n-config-provider
    :theme="setting.theme.isDark ? darkTheme : lightTheme"
    :theme-overrides="{
      common: { bodyColor: setting.theme.bodyColor },
    }"
  >
    <n-global-style />

    <ReaderSettingDialog v-model:show="showModal" />

    <div class="content">
      <ResultView
        :result="chapterResult"
        :showEmpty="() => false"
        v-slot="{ value: chapter }"
      >
        <n-h2 style="text-align: center">
          <n-a :href="buildWebChapterUrl(providerId, novelId, chapterId)">{{
            chapter.titleJp
          }}</n-a>
          <br />
          <n-text depth="3">{{ chapter.titleZh }}</n-text>
        </n-h2>

        <n-space align="center" justify="space-between" style="width: 100%">
          <ReuseChapterLink :id="chapter.prevId">上一章</ReuseChapterLink>
          <RouterNA :to="`/novel/${providerId}/${novelId}`">
            <n-button quaternary type="primary">目录</n-button>
          </RouterNA>
          <n-button quaternary type="primary" @click="showModal = true">
            设置
          </n-button>
          <ReuseChapterLink :id="chapter.nextId">下一章</ReuseChapterLink>
        </n-space>

        <n-divider />

        <div id="chapter-content">
          <template v-for="p in getTextList(chapter)">
            <template v-if="p && 'text' in p">
              <n-popconfirm
                v-if="p.popover"
                placement="top-start"
                positive-text="提交"
                :negative-text="null"
                @positive-click="createWebIncorrectCase(p.popover, p.text)"
              >
                <template #trigger>
                  <n-p :class="{ secondary: p.secondary }">
                    {{ p.text }}
                  </n-p>
                </template>
                <span>这句Sakura翻译不对？请提交帮助我们改进。</span>
              </n-popconfirm>

              <n-p v-else :class="{ secondary: p.secondary }">
                {{ p.text }}
              </n-p>
            </template>
            <br v-else-if="!p" />
            <img
              v-else
              :src="p.imageUrl"
              :alt="p.imageUrl"
              style="max-width: 100%; object-fit: scale-down"
            />
          </template>
        </div>

        <n-divider />

        <n-space align="center" justify="space-between" style="width: 100%">
          <ReuseChapterLink :id="chapter.prevId">上一章</ReuseChapterLink>
          <ReuseChapterLink :id="chapter.nextId">下一章</ReuseChapterLink>
        </n-space>
      </ResultView>
    </div>
  </n-config-provider>
</template>

<style scoped>
#chapter-content p {
  font-size: v-bind('setting.fontSize');
  color: v-bind("setting.theme.isDark ? 'white' : 'black'");
  opacity: v-bind('setting.mixZhOpacity');
}
#chapter-content .secondary {
  opacity: v-bind('setting.mixJpOpacity');
}
.content {
  max-width: 800px;
  margin: 0 auto;
  padding-left: 24px;
  padding-right: 24px;
  padding-bottom: 48px;
}
@media only screen and (max-width: 600px) {
  .content {
    padding-left: 12px;
    padding-right: 12px;
  }
}
</style>
