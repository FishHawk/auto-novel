import { MD5 } from 'crypto-es/lib/md5';

import { Locator } from '@/data';
import { Glossary } from '@/model/Glossary';

import { BaiduTranslator, BaiduTranslatorConfig } from './translator_baidu';
import { OpenAiTranslator, OpenAiTranslatorConfig } from './translator_openai';
import { SakuraTranslator, SakuraTranslatorConfig } from './translator_sakura';
import { YoudaoTranslator, YoudaoTranslatorConfig } from './translator_youdao';
import { SegmentTranslator } from './type';

interface SegmentCache {
  cacheKey(segIndex: number, seg: string[], extra?: any): string;
  get(cacheKey: string): Promise<string[] | undefined>;
  save(cacheKey: string, output: string[]): Promise<void>;
}

const createSegIndexedDbCache = async (
  storeName: 'gpt-seg-cache' | 'sakura-seg-cache'
) => {
  return <SegmentCache>{
    cacheKey: (_segIndex: number, seg: string[], extra?: any): string =>
      MD5(JSON.stringify({ seg, extra })).toString(),

    get: (hash: string): Promise<string[] | undefined> =>
      Locator.cachedSegRepository().then((repo) => repo.get(storeName, hash)),

    save: (hash: string, text: string[]): Promise<void> =>
      Locator.cachedSegRepository()
        .then((repo) => repo.create(storeName, hash, text))
        .then(() => {}),
  };
};

export type TranslatorConfig =
  | ({ id: 'baidu' } & BaiduTranslatorConfig)
  | ({ id: 'youdao' } & YoudaoTranslatorConfig)
  | ({ id: 'gpt' } & OpenAiTranslatorConfig)
  | ({ id: 'sakura' } & SakuraTranslatorConfig);

export class Translator {
  segTranslator: SegmentTranslator;
  segCache?: SegmentCache;

  constructor(segTranslator: SegmentTranslator, segCache?: SegmentCache) {
    this.segTranslator = segTranslator;
    this.segCache = segCache;
  }

  async translate(
    input: string[],
    glossary: Glossary,
    signal?: AbortSignal
  ): Promise<string[]> {
    return emptyLineFilterWrapper(input, async (input) => {
      if (input.length === 0) return [];

      let output: string[][] = [];
      const segs = this.segTranslator.createSegments(input);
      const size = segs.length;
      for (const [index, seg] of segs.entries()) {
        const segOutput = await this.translateSeg(
          seg,
          { index, size },
          glossary,
          signal
        );
        output.push(segOutput);
      }
      return output.flat();
    });
  }

  async translateSeg(
    seg: string[],
    segInfo: { index: number; size: number },
    glossary: Glossary,
    signal?: AbortSignal
  ) {
    let cacheKey: string | null = null;
    if (this.segCache) {
      try {
        let extra: any = { glossary };
        if (this.segTranslator instanceof SakuraTranslator) {
          extra.model = this.segTranslator.model;
        }
        cacheKey = this.segCache.cacheKey(segInfo.index, seg, extra);
        const cachedSegOutput = await this.segCache.get(cacheKey);
        if (cachedSegOutput && cachedSegOutput.length === seg.length) {
          this.segTranslator.log(
            `分段${segInfo.index + 1}/${segInfo.size} 从缓存恢复`
          );
          return cachedSegOutput;
        }
      } catch (e) {
        this.segTranslator.log(`缓存读取失败：${e}`);
      }
    }

    const segOutput = await this.segTranslator.translate(
      seg,
      segInfo,
      glossary,
      signal
    );
    if (segOutput.length !== seg.length) {
      throw new Error('翻译器行数不匹配，请反馈给站长');
    }

    if (this.segCache && cacheKey !== null) {
      try {
        await this.segCache.save(cacheKey, segOutput);
      } catch (e) {
        this.segTranslator.log(`缓存保存失败：${e}`);
      }
    }

    return segOutput;
  }

  static async createSegmentTranslator(
    config: TranslatorConfig
  ): Promise<SegmentTranslator> {
    if (config.id === 'baidu') {
      return await new BaiduTranslator(config).init();
    } else if (config.id === 'youdao') {
      return await new YoudaoTranslator(config).init();
    } else if (config.id === 'gpt') {
      return new OpenAiTranslator(config);
    } else {
      return await new SakuraTranslator(config).init();
    }
  }

  static async create(config: TranslatorConfig, cache: boolean) {
    const segTranslator = await this.createSegmentTranslator(config);
    let segCache: SegmentCache | undefined = undefined;
    if (cache) {
      if (config.id === 'gpt') {
        segCache = await createSegIndexedDbCache('gpt-seg-cache');
      } else if (config.id === 'sakura') {
        segCache = await createSegIndexedDbCache('sakura-seg-cache');
      }
    }
    return new Translator(segTranslator, segCache);
  }
}

const filterInput = (input: string[]) =>
  input
    .map((line) => line.replace(/\r?\n|\r/g, ''))
    .filter((line) => !(line.trim() === '' || line.startsWith('<图片>')));

const recoverOutput = (input: string[], output: string[]) => {
  const recoveredOutput: string[] = [];
  for (const line of input) {
    const realLine = line.replace(/\r?\n|\r/g, '');
    if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
      recoveredOutput.push(line);
    } else {
      const outputLine = output.shift();
      recoveredOutput.push(outputLine!);
    }
  }
  if (recoveredOutput.length !== input.length) {
    throw Error('重建翻译长度不匹配，不应当出现');
  }
  return recoveredOutput;
};

const emptyLineFilterWrapper = async (
  input: string[],
  callback: (input: string[]) => Promise<string[]>
) => {
  const filteredInput = filterInput(input);
  const output = await callback(filteredInput);
  const recoveredOutput = recoverOutput(input, output);
  return recoveredOutput;
};
