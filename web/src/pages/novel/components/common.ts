import { WebNovelTocItemDto } from '@/model/WebNovel';

export type ReadableTocItem = WebNovelTocItemDto & {
  key: number;
  order?: number;
};
