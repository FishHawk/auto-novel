import { KyInstance } from 'ky/distribution/types/ky';

import { Glossary, SegmentTranslator } from './type';
import { BaiduTranslator, YoudaoTranslator } from './tradition';
import { OpenAiTranslator } from './openai';

export interface SegmentCache {
  cacheKey(segIndex: number, seg: string[], glossary?: Glossary): string;
  get(cacheKey: string): string[] | null;
  save(cacheKey: string, output: string[]): void;
}

export type TranslatorId = 'baidu' | 'youdao' | 'gpt';

export interface TranslatorConfig {
  client: KyInstance;
  glossary?: Glossary;
  accessToken?: string;
  log: (message: string) => void;
}

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
    let cacheKey: string = '';
    if (this.segCache) {
      cacheKey = this.segCache.cacheKey(
        segInfo.index,
        seg,
        this.segTranslator.glossary
      );
      const cachedSegOutput = this.segCache.get(cacheKey);
      if (cachedSegOutput && cachedSegOutput.length === seg.length) {
        return cachedSegOutput;
      }
    }

    const segOutput = await this.segTranslator.translate(seg, segInfo);

    if (cacheKey && this.segCache) {
      this.segCache.save(cacheKey, seg);
    }

    return segOutput;
  }

  static async create(id: TranslatorId, config: TranslatorConfig) {
    async function createSegmentTranslator(
      id: TranslatorId,
      config: TranslatorConfig
    ): Promise<SegmentTranslator> {
      if (id === 'baidu') {
        return await new BaiduTranslator(
          config.client,
          config.log,
          config.glossary ?? {}
        ).init();
      } else if (id === 'youdao') {
        return await new YoudaoTranslator(
          config.client,
          config.log,
          config.glossary ?? {}
        ).init();
      } else {
        if (!config.accessToken) {
          throw new Error('Gpt翻译器需要Token');
        }
        return new OpenAiTranslator(
          config.client,
          config.log,
          config.glossary ?? {},
          config.accessToken
        );
      }
    }
    const segmentTranslator = await createSegmentTranslator(id, config);
    return new Translator(segmentTranslator);
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
