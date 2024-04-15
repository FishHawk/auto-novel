import { WebNovelDto, WebNovelTocItemDto } from '@/model/WebNovel';

export type ReadableTocItem = WebNovelTocItemDto & {
  key: number;
  order?: number;
};

export type WebNovelVM = Omit<WebNovelDto, 'toc'> & {
  toc: ReadableTocItem[];
};
