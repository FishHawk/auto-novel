import { computed, ComputedRef } from 'vue';
import { WebNovelDto } from '@/model/WebNovel';

import { ReadableTocItem } from './common';

export const useToc = (novel: WebNovelDto) => {
  const toc = computed(() => {
    const novelToc = novel.toc as ReadableTocItem[];
    let order = 0;
    for (const [index, it] of novelToc.entries()) {
      it.key = index;
      it.order = it.chapterId ? order : undefined;
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
    return undefined;
  });
  return { lastReadChapter };
};
