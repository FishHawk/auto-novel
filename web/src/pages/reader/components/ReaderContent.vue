<script lang="ts" setup>
import { ErrorOutlineOutlined } from '@vicons/material';
import { useOsTheme } from 'naive-ui';
import { useScroll } from '@vueuse/core';

import { Locator } from '@/data';
import { GenericNovelId } from '@/model/Common';
import { WebNovelChapterDto } from '@/model/WebNovel';

import { buildParagraphs } from './BuildParagraphs';

const props = defineProps<{
  gnid: GenericNovelId;
  chapterId: string;
  chapter: WebNovelChapterDto;
}>();

const message = useMessage();
const osThemeRef = useOsTheme();

const paragraphs = computed(() => buildParagraphs(props.gnid, props.chapter));

const readPositionRepository = Locator.readPositionRepository();

const addReadPosition = () => {
  readPositionRepository.addPosition(props.gnid, {
    chapterId: props.chapterId,
    scrollY: window.scrollY,
  });
};

useScroll(window, { onScroll: addReadPosition, throttle: 1000 });

onMounted(async () => {
  const readPosition = readPositionRepository.getPosition(props.gnid);
  if (readPosition && readPosition.chapterId === props.chapterId) {
    // hacky: 等待段落显示完成
    await nextTick();
    await nextTick();
    window.scrollTo({ top: readPosition.scrollY });
  }
});

const { setting } = Locator.readerSettingRepository();
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

const textUnderlineOffset = computed(() => {
  const fontSize = setting.value.fontSize;
  const offset = Math.round(fontSize / 4);
  return `${offset}px`;
});
</script>

<template>
  <div id="chapter-content">
    <template
      v-for="(p, index) of paragraphs"
      :key="`${chapter.prevId}/${index}`"
    >
      <n-p
        v-if="p && 'text' in p"
        :class="{ secondary: p.secondary }"
        :aria-hidden="!p.needSpeak"
      >
        <n-tag v-if="p.source" size="small" class="secondary">
          {{ p.source }}
        </n-tag>
        <span class="leading-space" v-if="!setting.trimLeadingSpaces">
          {{ p.leadingSpace }}
        </span>
        <span class="text-content">{{ p.text }}</span>
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
  font-weight: v-bind('setting.fontWeight');
  font-size: v-bind('`${setting.fontSize}px`');
  margin: v-bind('`${setting.fontSize * setting.lineSpace}px 0`');
  color: v-bind('fontColor');
  opacity: v-bind('setting.mixZhOpacity');
}
#chapter-content p .text-content {
  text-decoration-line: v-bind(
    "setting.textUnderline === 'none' ? 'none' : 'underline'"
  );
  text-decoration-style: v-bind('setting.textUnderline');
  text-decoration-thickness: v-bind(
    "setting.textUnderline === 'dotted' ? '2px' : '1px'"
  );
  text-decoration-color: #999999;
  text-underline-offset: v-bind('textUnderlineOffset');
}
#chapter-content .secondary {
  opacity: v-bind('setting.mixJpOpacity');
}
</style>
