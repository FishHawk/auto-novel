import { isEqual } from 'lodash-es';

import { Glossary } from '@/model/Glossary';

import { BaiduTranslator } from './TranslatorBaidu';
import { OpenAiTranslator } from './TranslatorOpenAi';
import { SakuraTranslator } from './TranslatorSakura';
import { YoudaoTranslator } from './TranslatorYoudao';
import {
  SegmentCache,
  SegmentTranslator,
  createSegIndexedDbCache,
} from './common';

export type TranslatorConfig =
  | ({ id: 'baidu' } & BaiduTranslator.Config)
  | ({ id: 'youdao' } & YoudaoTranslator.Config)
  | ({ id: 'gpt' } & OpenAiTranslator.Config)
  | ({ id: 'sakura' } & SakuraTranslator.Config);

export class Translator {
  segTranslator: SegmentTranslator;
  segCache?: SegmentCache;

  constructor(segTranslator: SegmentTranslator, segCache?: SegmentCache) {
    this.segTranslator = segTranslator;
    this.segCache = segCache;
  }

  allowUpload() {
    return !(
      this.segTranslator instanceof SakuraTranslator &&
      !this.segTranslator.allowUpload()
    );
  }

  sakuraVersion() {
    if (this.segTranslator instanceof SakuraTranslator) {
      return this.segTranslator.model.version;
    } else {
      return '';
    }
  }

  async translate(
    textJp: string[],
    extra?: {
      glossary?: Glossary;
      oldTextZh?: string[] | undefined;
      oldGlossary?: Glossary;
      signal?: AbortSignal;
    }
  ): Promise<string[]> {
    let { glossary, oldTextZh, oldGlossary, signal } = extra || {};
    glossary = glossary || {};
    oldGlossary = oldGlossary || {};

    if (oldTextZh !== undefined && textJp.length !== oldTextZh.length) {
      throw new Error('旧版翻译行数不匹配。不应当出现，请反馈给站长。');
    }

    const textZh = await emptyLineFilterWrapper(textJp, async (textJp) => {
      if (textJp.length === 0) return [];

      const resultsZh: string[][] = [];
      const segsJp = this.segTranslator.segmentor(textJp);
      const size = segsJp.length;
      for (const [index, [segJp, oldSegZh]] of segsJp.entries()) {
        if (oldSegZh !== undefined) {
          const segGlossary = filterGlossary(glossary, segJp);
          const segOldGlossary = filterGlossary(oldGlossary, segJp);
          if (isEqual(segGlossary, segOldGlossary)) {
            // 该分段术语表无变化，无需重新翻译
            resultsZh.push(oldSegZh);
            continue;
          }
        }
        const segZh = await this.translateSeg(
          segJp,
          { index, size },
          glossary,
          signal
        );
        if (segJp.length !== segZh.length) {
          throw new Error('翻译结果行数不匹配。不应当出现，请反馈给站长。');
        }
        if (segZh.some((it) => it.trim().length === 0)) {
          throw new Error('翻译结果存在空行。不应当出现，请反馈给站长。');
        }
        resultsZh.push(segZh);
      }
      return resultsZh.flat();
    });

    return textZh;
  }

  async translateSeg(
    seg: string[],
    segInfo: { index: number; size: number },
    glossary: Glossary,
    signal?: AbortSignal
  ) {
    let cacheKey: string | undefined;
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

    if (this.segCache && cacheKey !== undefined) {
      try {
        await this.segCache.save(cacheKey, segOutput);
      } catch (e) {
        this.segTranslator.log(`缓存保存失败：${e}`);
      }
    }

    return segOutput;
  }
}

export namespace Translator {
  const createSegmentTranslator = async (
    config: TranslatorConfig
  ): Promise<SegmentTranslator> => {
    if (config.id === 'baidu') {
      return BaiduTranslator.create(config);
    } else if (config.id === 'youdao') {
      return YoudaoTranslator.create(config);
    } else if (config.id === 'gpt') {
      return OpenAiTranslator.create(config);
    } else {
      return SakuraTranslator.create(config);
    }
  };

  export const create = async (config: TranslatorConfig, cache: boolean) => {
    const segTranslator = await createSegmentTranslator(config);
    let segCache: SegmentCache | undefined = undefined;
    if (cache) {
      if (config.id === 'gpt') {
        segCache = await createSegIndexedDbCache('gpt-seg-cache');
      } else if (config.id === 'sakura') {
        segCache = await createSegIndexedDbCache('sakura-seg-cache');
      }
    }
    return new Translator(segTranslator, segCache);
  };
}

const filterGlossary = (glossary: Glossary, text: string[]) => {
  const filteredGlossary: Glossary = {};
  for (const wordJp in glossary) {
    if (text.some((it) => it.includes(wordJp))) {
      filteredGlossary[wordJp] = glossary[wordJp];
    }
  }
  return filteredGlossary;
};

const emptyLineFilterWrapper = async (
  input: string[],
  callback: (input: string[]) => Promise<string[]>
) => {
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
    return recoveredOutput;
  };

  const filteredInput = filterInput(input);
  const output = await callback(filteredInput);
  const recoveredOutput = recoverOutput(input, output);
  return recoveredOutput;
};
