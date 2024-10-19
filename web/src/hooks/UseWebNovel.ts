import { computed, ComputedRef } from 'vue';
import { WebNovelDto, ReadableTocItem } from '@/model/WebNovel';

export const useToc = (novel: WebNovelDto) => {
  const toc = computed(() => {
    const novelToc = novel.toc as ReadableTocItem[];
    let order = 0;
    for (const [index, it] of novelToc.entries()) {
      it.key = index;
      it.order = it.chapterId ? order : void 0;
      if (it.chapterId) order += 1;
    }
    return novelToc;
  });
  return { toc };
};

export const useLastReadChapter = (
  novel: WebNovelDto,
  toc: ComputedRef<ReadableTocItem[]>,
) => {
  const lastReadChapter = computed(() => {
    if (novel.lastReadChapterId) {
      return toc.value.find((it) => it.chapterId === novel.lastReadChapterId);
    }
    return void 0;
  });
  return { lastReadChapter };
};
