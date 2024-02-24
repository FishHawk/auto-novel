import { RouteParams } from 'vue-router';

import { ApiWebNovel, WebNovelChapterDto } from '@/data/api/api_web_novel';
import { Result, runCatching } from '@/data/result';
import { PersonalVolumesManager } from '@/data/translator';
import { buildWebChapterUrl } from '@/data/util_web';

export type NovelInfo = (
  | { type: 'web'; providerId: string; novelId: string }
  | { type: 'workspace'; novelId: string }
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
      novelId: params.novelId as string,
      pathPrefix: `/workspace/reader/${novelId}`,
      getChapterUrl: (_) => '/workspace',
    };
  }
};

export const getChapter = (
  novelPath: NovelInfo,
  chapterId: string
): Promise<Result<WebNovelChapterDto>> => {
  if (novelPath.type === 'web') {
    return ApiWebNovel.getChapter(
      novelPath.providerId,
      novelPath.novelId,
      chapterId
    );
  } else {
    return runCatching(
      PersonalVolumesManager.getVolumeWebNovelChapterDto(
        novelPath.novelId,
        chapterId
      )
    );
  }
};
