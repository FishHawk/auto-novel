import { beforeEach, describe, expect, it, vi } from 'vitest';
import type {
  TranslateTaskParams,
  WebTranslateTask,
} from '../src/model/Translator';

const transport = vi.hoisted(() => ({ fetch: vi.fn() }));
vi.mock('../src/api/client', async () => {
  const { default: ky } = await import('ky');
  return {
    client: ky.create({
      prefix: 'https://books.test/api/',
      retry: 0,
      fetch: transport.fetch,
    }),
  };
});
vi.mock('../src/api', async () => ({
  WebNovelApi: (await import('../src/api/WebNovelApi')).WebNovelApi,
}));

import { WebTranslationTask } from '../src/domain/translator/TranslationTask/WebTranslationTask';

const params: TranslateTaskParams = {
  level: 'normal',
  forceMetadata: false,
  useBrowserCrawler: false,
  startIndex: 0,
  endIndex: 65535,
};
const fixture = (): WebTranslateTask => ({
  titleJp: 'ウマ娘の物語',
  introductionJp: '最初の行\n\n最後の行',
  glossaryUuid: 'glossary-1',
  glossary: { ウマ娘: '賽馬娘' },
  toc: [
    { chapterId: '1', titleJp: '第１話　出会い' },
    { chapterId: '2', titleJp: '第２話　出会い' },
    { titleJp: '閑話' },
  ],
});
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => (resolve = done));
  return { promise, resolve };
}

describe('Sakura web metadata HTTP boundary', () => {
  let source: WebTranslateTask;
  let posts: { request: Request; body: unknown }[];
  beforeEach(() => {
    source = fixture();
    posts = [];
    transport.fetch.mockReset().mockImplementation(async (input, init) => {
      const request = new Request(input, init);
      if (request.method === 'GET') return Response.json(source);
      posts.push({ request, body: await request.json() });
      return new Response('ok');
    });
  });

  async function task(
    forceMetadata = false,
    translatorId: 'sakura' | 'gpt' = 'sakura',
  ) {
    const task = new WebTranslationTask('syosetu', 'n1', translatorId, {
      ...params,
      forceMetadata,
    });
    await task.initMeta();
    return task;
  }

  it('uploads title, multiline introduction and deduplicated TOC using the active run signal', async () => {
    const current = await task();
    const run = new AbortController();
    const translate = vi.fn(async (texts: string[]) => {
      expect(texts).toEqual(['ウマ娘の物語', '最初の行\n\n最後の行', '出会い']);
      return ['賽馬娘的故事', '第一行\n\n最後一行', '相遇'];
    });

    await current.translateMetadata(translate, run.signal, () => true);

    expect(posts).toHaveLength(1);
    expect(posts[0].request.url).toBe(
      'https://books.test/api/novel/syosetu/n1/translate-v2/sakura/metadata',
    );
    expect(posts[0].body).toEqual({
      title: '賽馬娘的故事',
      introduction: '第一行\n\n最後一行',
      toc: {
        '第１話　出会い': '第１话　相遇',
        '第２話　出会い': '第２话　相遇',
        閑話: '闲话',
      },
    });
    run.abort();
    expect(posts[0].request.signal.aborted).toBe(true);
  });

  it('retranslates deterministic TOC on an already translated novel without a model call or replacing title/introduction', async () => {
    source.titleZh = '既有標題';
    source.introductionZh = '既有簡介';
    source.toc = [
      {
        chapterId: '1',
        titleJp: '第1話',
        titleZh: '舊目錄',
        glossaryUuid: 'glossary-1',
      },
      { titleJp: '幕間', titleZh: '舊分組' },
    ];
    const current = await task(true);
    const translate = vi.fn();
    expect(current.chapters).toEqual([]);
    expect(current.needsMetadata).toBe(true);

    await current.translateMetadata(translate, undefined, () => true);

    expect(translate).not.toHaveBeenCalled();
    expect(posts.map(({ body }) => body)).toEqual([
      { toc: { 第1話: '第1话', 幕間: '幕间' } },
    ]);
    expect((await task(true, 'gpt')).needsMetadata).toBe(false);
  });

  it('does not upload a result that completes after the run is stopped', async () => {
    const current = await task();
    const run = new AbortController();
    const translated = deferred<string[]>();
    const operation = current.translateMetadata(
      () => translated.promise,
      run.signal,
      () => true,
    );
    const rejected = expect(operation).rejects.toMatchObject({
      name: 'AbortError',
    });

    run.abort();
    translated.resolve(['標題', '簡介\n\n結尾', '相遇']);

    await rejected;
    expect(posts).toEqual([]);
  });

  it('checks model eligibility before translation and again before upload', async () => {
    const current = await task();
    const translate = vi.fn(async () => ['標題', '簡介\n\n結尾', '相遇']);
    await expect(
      current.translateMetadata(translate, undefined, () => false),
    ).rejects.toThrow('上传检查');
    expect(translate).not.toHaveBeenCalled();

    let eligible = true;
    const translated = deferred<string[]>();
    const operation = current.translateMetadata(
      () => translated.promise,
      undefined,
      () => eligible,
    );
    const rejected = expect(operation).rejects.toThrow('上传检查');
    eligible = false;
    translated.resolve(['標題', '簡介\n\n結尾', '相遇']);

    await rejected;
    expect(posts).toEqual([]);
  });

  it('rejects an incomplete translation instead of uploading misplaced metadata', async () => {
    const current = await task();
    await expect(
      current.translateMetadata(
        async () => ['標題'],
        undefined,
        () => true,
      ),
    ).rejects.toThrow('数量不匹配');
    expect(posts).toEqual([]);
  });
});
