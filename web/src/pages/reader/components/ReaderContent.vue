<script lang="ts" setup>
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

const hexToRgba = (hex: string, alpha: number): string => {
  // const shorthandRegex = /^#?([a-f\d])([a-f\d])([a-f\d])$/i;
  // hex = hex.replace(shorthandRegex, (m, r, g, b) => {
  //   return r + r + g + g + b + b;
  // });

  const result = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!result) {
    return `rgba(0, 0, 0, ${alpha})`;
  }
  const r = parseInt(result[1], 16);
  const g = parseInt(result[2], 16);
  const b = parseInt(result[3], 16);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
};

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

const firstUnderlineColor = computed(() => {
  return hexToRgba(
    setting.value.theme.fontColor,
    setting.value.mixZhOpacity * 0.5,
  );
});

const secondUnderlineColor = computed(() => {
  return hexToRgba(
    setting.value.theme.fontColor,
    setting.value.mixJpOpacity * 0.5,
  );
});
</script>

<template>
  <div id="chapter-content">
    <template
      v-for="(p, index) of paragraphs"
      :key="`${chapter.prevId}/${index}`"
    >
      <n-p v-if="p && 'text' in p" :aria-hidden="!p.needSpeak">
        <span v-if="setting.enableSourceLabel && p.source" class="source">
          {{ p.source }}
        </span>
        <span v-if="!setting.trimLeadingSpaces">
          {{ p.indent }}
        </span>
        <span :class="[p.secondary ? 'second' : 'first', 'text-content']">
          {{ p.text }}
        </span>
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
}
#chapter-content p .source {
  display: inline-block;
  user-select: none;
  width: 1em;
  text-align: center;
  opacity: 0.4;
  font-size: 0.75em;
  margin-right: 0.5em;
}
#chapter-content p .first {
  opacity: v-bind('setting.mixZhOpacity');
}
#chapter-content p .second {
  opacity: v-bind('setting.mixJpOpacity');
}
#chapter-content p .text-content {
  text-decoration-line: v-bind(
    "setting.textUnderline === 'none' ? 'none' : 'underline'"
  );
  text-decoration-style: v-bind('setting.textUnderline');
  text-decoration-thickness: 1px;
  text-underline-offset: v-bind('textUnderlineOffset');
}
#chapter-content p .first {
  text-decoration-color: v-bind('firstUnderlineColor');
}
#chapter-content p .second {
  text-decoration-color: v-bind('secondUnderlineColor');
}
</style>
