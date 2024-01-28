import { WebNovelDto, WebNovelTocItemDto } from '@/data/api/api_web_novel';

export type ReadableTocItem = WebNovelTocItemDto & {
  index: number;
  order?: number;
};

export type WebNovelVM = Omit<WebNovelDto, 'toc'> & {
  toc: ReadableTocItem[];
  lastReadChapter: ReadableTocItem;
};
