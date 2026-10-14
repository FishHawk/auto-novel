import { MD5 } from 'crypto-es/lib/md5';
import type { Segment, SegmentCache } from '@auto-novel/translator';
import { TranslationCacheRepo } from '@/repos';

export class IndexedDbSegmentCache implements SegmentCache {
  constructor(private storeName: 'gpt-seg-cache' | 'sakura-seg-cache') {}

  async get(segment: Segment): Promise<string[] | undefined> {
    const key = this.computeKey(segment);
    const cached = await TranslationCacheRepo.get(this.storeName, key);
    // 旧版逐行翻译可能缓存了含换行的译文，会导致上传行数不匹配，视为未命中
    if (
      cached?.length !== segment.lines.length ||
      cached.some((line) => line.includes('\n'))
    ) {
      return undefined;
    }
    return cached;
  }

  async set(segment: Segment, translatedLines: string[]): Promise<void> {
    const key = this.computeKey(segment);
    await TranslationCacheRepo.create(this.storeName, key, translatedLines);
  }

  clear(): Promise<void> {
    return TranslationCacheRepo.clear(this.storeName);
  }

  private computeKey(segment: Segment): string {
    return MD5(
      JSON.stringify({
        lines: segment.lines,
        glossary: segment.context?.glossary,
      }),
    ).toString();
  }
}
