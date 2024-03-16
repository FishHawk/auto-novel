import { WebNovelDto, WebNovelTocItemDto } from "@/model/WebNovel";

export type ReadableTocItem = WebNovelTocItemDto & {
  index: number;
  order?: number;
};

export type WebNovelVM = Omit<WebNovelDto, 'toc'> & {
  toc: ReadableTocItem[];
  lastReadChapter: ReadableTocItem;
};
