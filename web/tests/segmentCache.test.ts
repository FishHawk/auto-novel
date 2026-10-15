import type { Segment } from '@auto-novel/translator';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { IndexedDbSegmentCache } from '../src/domain/translator/IndexedDbSegmentCache';
import { TranslationCacheRepo } from '../src/repos';

vi.mock('../src/repos', () => ({
  TranslationCacheRepo: {
    get: vi.fn(),
    create: vi.fn(),
  },
}));

const segment = (lines = ['あ', '', 'い']): Segment => ({
  id: 'segment',
  order: 0,
  lines,
  context: { glossary: { あ: '甲' } },
  onStart: () => {},
  onComplete: () => {},
  onError: () => {},
});

beforeEach(() => vi.resetAllMocks());

describe('IndexedDbSegmentCache', () => {
  it('reads valid GPT and Sakura entries using the same key as their write', async () => {
    const source = segment();
    const translated = ['甲', '', '乙'];
    for (const store of ['gpt-seg-cache', 'sakura-seg-cache'] as const) {
      const cache = new IndexedDbSegmentCache(store);
      await cache.set(source, translated);
      const [writtenStore, key, writtenLines] = vi
        .mocked(TranslationCacheRepo.create)
        .mock.calls.at(-1)!;
      expect(writtenStore).toBe(store);
      expect(writtenLines).toBe(translated);
      vi.mocked(TranslationCacheRepo.get).mockResolvedValueOnce(translated);

      expect(await cache.get(source)).toBe(translated);
      expect(TranslationCacheRepo.get).toHaveBeenLastCalledWith(store, key);
    }
  });

  it('returns a cache miss when no entry exists', async () => {
    const cache = new IndexedDbSegmentCache('sakura-seg-cache');
    vi.mocked(TranslationCacheRepo.get).mockResolvedValueOnce(undefined);

    expect(await cache.get(segment())).toBeUndefined();
  });

  it('retranslates corrupted Sakura image markers while retaining intact entries', async () => {
    const cache = new IndexedDbSegmentCache('sakura-seg-cache');
    const source = segment(['あ', '<图片>image-url', 'い']);
    vi.mocked(TranslationCacheRepo.get).mockResolvedValueOnce([
      '甲',
      '图片描述',
      '乙',
    ]);
    expect(await cache.get(source)).toBeUndefined();
    const valid = ['甲', '<图片>image-url', '乙'];
    vi.mocked(TranslationCacheRepo.get).mockResolvedValueOnce(valid);
    expect(await cache.get(source)).toBe(valid);
  });

  it.each<[string, string[]]>([
    ['different line count', ['甲', '乙']],
    ['LF inside a line', ['甲\n补充', '', '乙']],
    ['invented text for a blank source', ['甲', '……', '乙']],
  ])('rejects an old cached translation with %s', async (_reason, cached) => {
    const cache = new IndexedDbSegmentCache('sakura-seg-cache');
    vi.mocked(TranslationCacheRepo.get).mockResolvedValueOnce(cached);

    expect(await cache.get(segment())).toBeUndefined();
  });
});
