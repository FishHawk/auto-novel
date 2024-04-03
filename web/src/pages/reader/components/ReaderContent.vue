<script lang="ts" setup>
import { ErrorOutlineOutlined } from '@vicons/material';
import { useOsTheme } from 'naive-ui';

import { SakuraRepository } from '@/data/api';
import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { WebNovelChapterDto } from '@/model/WebNovel';
import { doAction } from '@/pages/util';
import { ReaderService } from '@/domain';

const props = defineProps<{
  gnid: GenericNovelId;
  chapterId: string;
  chapter: WebNovelChapterDto;
}>();

const message = useMessage();
const osThemeRef = useOsTheme();

const paragraphs = computed(() => {
  const chapter = props.chapter;
  return ReaderService.getParagraphs(props.gnid, chapter);
});

const readPositionRepository = Locator.readPositionRepository();

const addReadPosition = () => {
  readPositionRepository.addPosition(props.gnid, {
    chapterId: props.chapterId,
    scrollY: window.scrollY,
  });
};

window.removeEventListener('beforeunload', addReadPosition);
window.addEventListener('beforeunload', addReadPosition);

onBeforeUnmount(addReadPosition);

onMounted(async () => {
  const readPosition = readPositionRepository.getPosition(props.gnid);
  if (readPosition && readPosition.chapterId === props.chapterId) {
    // hacky: 等待段落显示完成
    await nextTick();
    await nextTick();
    window.scrollTo({ top: readPosition.scrollY });
  }
});

const createWebIncorrectCase = async (
  index: number,
  chapter: WebNovelChapterDto
) => {
  if (props.gnid.type !== 'web') return;

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

  await doAction(
    SakuraRepository.createWebIncorrectCase({
      providerId: props.gnid.providerId,
      novelId: props.gnid.novelId,
      chapterId: props.chapterId,
      jp,
      zh,
      contextJp,
      contextZh,
    }),
    '提交',
    message
  );
};

const setting = Locator.readerSettingRepository().ref;
const fontColor = computed(() => {
  const theme = setting.value.theme;
  if (theme.mode === 'custom') {
    return theme.fontColor;
  } else {
    let specificTheme: 'light' | 'dark' = 'light';
    if (theme.mode !== 'system') {
      specificTheme = theme.mode;
    } else if (osThemeRef.value) {
      specificTheme = osThemeRef.value;
    }
    return specificTheme === 'light' ? 'black' : 'white';
  }
});
</script>

<template>
  <div id="chapter-content">
    <template
      v-for="(p, index) of paragraphs"
      :key="`${chapter.prevId}/${index}`"
    >
      <n-p v-if="p && 'text' in p" :class="{ secondary: p.secondary }">
        {{ p.text }}
        <n-popconfirm
          v-if="p.popover !== undefined"
          :show-icon="false"
          placement="top-start"
          positive-text="提交"
          :negative-text="null"
          @positive-click="createWebIncorrectCase(p.popover, chapter)"
        >
          <template #trigger>
            <c-button
              text
              style="opacity: 0.5"
              :icon="ErrorOutlineOutlined"
              @action="(e: MouseEvent) => e.stopPropagation()"
            />
          </template>
          这段话翻得不准确？
        </n-popconfirm>
      </n-p>
      <br v-else-if="!p" />
      <img
        v-else
        :src="p.imageUrl"
        :alt="p.imageUrl"
        style="max-width: 100%; object-fit: scale-down"
        loading="lazy"
      />
    </template>
  </div>
</template>

<style scoped>
#chapter-content {
  min-height: 65vh;
}
#chapter-content p {
  font-size: v-bind('`${setting.fontSize}px`');
  margin: v-bind('`${setting.fontSize * setting.lineSpace}px 0`');
  color: v-bind('fontColor');
  opacity: v-bind('setting.mixZhOpacity');
}
#chapter-content .secondary {
  opacity: v-bind('setting.mixJpOpacity');
}
</style>
@/data/stores/ReadPosition
