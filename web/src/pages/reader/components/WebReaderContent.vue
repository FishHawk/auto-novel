<script lang="ts" setup>
import { useMessage } from 'naive-ui';
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { ApiSakura } from '@/data/api/api_sakura';
import { WebNovelChapterDto } from '@/data/api/api_web_novel';
import { useReaderSettingStore } from '@/data/stores/reader_setting';
import { TranslatorId } from '@/data/translator/translator';

const props = defineProps<{
  providerId: string;
  novelId: string;
  chapterId: string;
  chapter: WebNovelChapterDto;
}>();

const setting = useReaderSettingStore();
const route = useRoute();
const message = useMessage();

type Paragraph =
  | { text: string; secondary: boolean; popover?: number }
  | { imageUrl: string }
  | null;

const paragraphs = computed(() => {
  const chapter = props.chapter;
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
          popover: style.popover === true ? i : undefined,
        });
      }
    }
  }
  return merged;
});

const createWebIncorrectCase = async (
  index: number,
  chapter: WebNovelChapterDto
) => {
  const jp = chapter.paragraphs[index];
  const zh = chapter.sakuraParagraphs!![index];

  function truncateParagraphs(
    paragraphsJp: string[],
    paragraphsZh: string[],
    maxLength: number
  ) {
    const truncatedJp: string[] = [];
    const truncatedZh: string[] = [];
    let currentLength = 0;

    for (let i = 0; i < paragraphsJp.length; i++) {
      const pJp = paragraphsJp[i];
      const pZh = paragraphsZh[i];
      if (pJp.trim().length === 0 || pJp.startsWith('<图片>')) {
        continue;
      }
      if (currentLength + pJp.length > maxLength) {
        break;
      }
      currentLength += pJp.length;
      truncatedJp.push(pJp);
      truncatedZh.push(pZh);
    }
    return { jp: truncatedJp, zh: truncatedZh };
  }

  const { jp: contextJpBefore, zh: contextZhBefore } = truncateParagraphs(
    chapter.paragraphs.slice(0, index).reverse(),
    chapter.sakuraParagraphs!.slice(0, index).reverse(),
    512 - jp.length
  );
  const { jp: contextJpAfter, zh: contextZhAfter } = truncateParagraphs(
    chapter.paragraphs.slice(index + 1, chapter.paragraphs.length),
    chapter.sakuraParagraphs!.slice(index + 1, chapter.paragraphs.length),
    512 - jp.length
  );

  const contextJp = [...contextJpBefore.reverse(), jp, ...contextJpAfter];
  const contextZh = [...contextZhBefore.reverse(), zh, ...contextZhAfter];

  const result = await ApiSakura.createWebIncorrectCase({
    providerId: props.providerId,
    novelId: props.novelId,
    chapterId: route.params.chapterId as string,
    jp,
    zh,
    contextJp,
    contextZh,
  });
  if (result.ok) {
    message.info('提交成功');
  } else {
    message.error('提交失败:' + result.error.message);
  }
};
</script>

<template>
  <div id="chapter-content">
    <template v-for="(p, index) in paragraphs" :key="chapterId + index">
      <template v-if="p && 'text' in p">
        <n-popconfirm
          v-if="p.popover !== undefined"
          placement="top-start"
          positive-text="提交"
          :negative-text="null"
          @positive-click="createWebIncorrectCase(p.popover, chapter)"
        >
          <template #trigger>
            <n-p
              :class="{ secondary: p.secondary }"
              @click="(e: MouseEvent) => e.stopPropagation()"
            >
              {{ p.text }}
            </n-p>
          </template>
          <span> 这段话Sakura翻译不准确？请提交帮助我们改进。 </span>
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
</template>

<style scoped>
#chapter-content {
  min-height: 60vh;
}
#chapter-content p {
  font-size: v-bind('setting.fontSize');
  color: v-bind(
    "setting.theme.fontColor ?? (setting.theme.isDark ? 'white' : 'black')"
  );
  opacity: v-bind('setting.mixZhOpacity');
}
#chapter-content .secondary {
  opacity: v-bind('setting.mixJpOpacity');
}
</style>
