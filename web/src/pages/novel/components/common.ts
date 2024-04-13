import { WebNovelDto, WebNovelTocItemDto } from '@/model/WebNovel';

export type ReadableTocItem = WebNovelTocItemDto & {
  order?: number;
};

export type WebNovelVM = Omit<WebNovelDto, 'toc'> & {
  toc: ReadableTocItem[];
};
