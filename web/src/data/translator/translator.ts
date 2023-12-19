import { createSegIndexedDbCache } from './cache';
import { BaiduTranslator, BaiduTranslatorConfig } from './translator_baidu';
import { OpenAiTranslator, OpenAiTranslatorConfig } from './translator_openai';
import { SakuraTranslator, SakuraTranslatorConfig } from './translator_sakura';
import { YoudaoTranslator, YoudaoTranslatorConfig } from './translator_youdao';
import { Glossary, SegmentTranslator } from './type';

export interface SegmentCache {
  cacheKey(segIndex: number, seg: string[], glossary?: Glossary): string;
  get(cacheKey: string): Promise<string[] | null>;
  save(cacheKey: string, output: string[]): Promise<void>;
}

export type TranslatorId = 'sakura' | 'baidu' | 'youdao' | 'gpt';

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

  async translate(input: string[]): Promise<string[]> {
    return emptyLineFilterWrapper(input, async (input) => {
      if (input.length === 0) return [];

      let output: string[][] = [];
      const segs = await this.segTranslator.createSegments(input);
      const size = segs.length;
      for (const [index, seg] of segs.entries()) {
        const segOutput = await this.translateSegWithCache(seg, {
          index,
          size,
        });
        output.push(segOutput);
      }
      return output.flat();
    });
  }

  async translateSegWithCache(
    seg: string[],
    segInfo: { index: number; size: number }
  ) {
    let cacheKey: string | null = null;
    if (this.segCache) {
      try {
        cacheKey = this.segCache.cacheKey(
          segInfo.index,
          seg,
          this.segTranslator.glossary
        );
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

    const segOutput = await this.segTranslator.translate(seg, segInfo);

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

  static async create(config: TranslatorConfig) {
    const segTranslator = await this.createSegmentTranslator(config);
    let segCache: SegmentCache | undefined = undefined;
    if (config.id === 'gpt') {
      segCache = await createSegIndexedDbCache('gpt-seg-cache');
    } else if (config.id === 'sakura') {
      segCache = await createSegIndexedDbCache('sakura-seg-cache');
    }
    return new Translator(segTranslator, segCache);
  }

  static async createWithoutCache(config: TranslatorConfig) {
    const segTranslator = await this.createSegmentTranslator(config);
    return new Translator(segTranslator, undefined);
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
