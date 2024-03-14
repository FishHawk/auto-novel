import { RouteParams } from 'vue-router';

import {
  ApiWebNovel,
  WebNovelChapterDto,
  WebNovelTocItemDto,
} from '@/data/api/api_web_novel';
import { LocalVolumeService } from '@/data/local';
import { Result, mapOk, runCatching } from '@/data/result';
import { buildWebChapterUrl } from '@/data/web/url';

export type NovelInfo = (
  | { type: 'web'; providerId: string; novelId: string }
  | { type: 'workspace'; volumeId: string }
) & {
  pathPrefix: string;
  novelUrl?: string;
  getChapterUrl: (chapterId: string) => string;
};

export const getNovelInfo = (path: string, params: RouteParams): NovelInfo => {
  if (path.startsWith('/novel')) {
    const providerId = params.providerId as string;
    const novelId = params.novelId as string;
    return {
      type: 'web',
      providerId,
      novelId,
      pathPrefix: `/novel/${providerId}/${novelId}`,
      novelUrl: `/novel/${providerId}/${novelId}`,
      getChapterUrl: (chapterId) =>
        buildWebChapterUrl(providerId, novelId, chapterId),
    };
  } else {
    const novelId = params.novelId as string;
    return {
      type: 'workspace',
      volumeId: params.novelId as string,
      pathPrefix: `/workspace/reader/${novelId}`,
      getChapterUrl: (_) => '/workspace',
    };
  }
};

type TocItem = WebNovelTocItemDto & { key: number };

export const getNovelToc = async (
  novelInfo: NovelInfo
): Promise<Result<TocItem[]>> => {
  if (novelInfo.type === 'web') {
    const result = await ApiWebNovel.getNovel(
      novelInfo.providerId,
      novelInfo.novelId
    );
    const newResult = mapOk(result, (novel) => {
      const toc = novel.toc as TocItem[];
      toc.forEach((it, index) => (it.key = index));
      return toc;
    });
    return newResult;
  } else {
    const getNovelTocInner = async (id: string) => {
      const metadata = await LocalVolumeService.getVolume(id);
      if (metadata === undefined) throw Error('小说不存在');
      return metadata.toc.map((it, index) => ({
        titleJp: it.chapterId,
        chapterId: it.chapterId,
        key: index,
      }));
    };
    return runCatching(getNovelTocInner(novelInfo.volumeId));
  }
};

export const getChapter = (
  novelInfo: NovelInfo,
  chapterId: string
): Promise<Result<WebNovelChapterDto>> => {
  if (novelInfo.type === 'web') {
    return ApiWebNovel.getChapter(
      novelInfo.providerId,
      novelInfo.novelId,
      chapterId
    );
  } else {
    return runCatching(
      LocalVolumeService.getReadableChapter(novelInfo.volumeId, chapterId)
    );
  }
};

export function isDarkColor(color: string) {
  const r = parseInt(color.substring(1, 3), 16);
  const g = parseInt(color.substring(3, 5), 16);
  const b = parseInt(color.substring(5, 7), 16);
  const brightness = (r * 299 + g * 587 + b * 114) / 1000;
  return brightness < 120;
}
