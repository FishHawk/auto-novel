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
  log: (message: string) => void;
  segTranslator: SegmentTranslator;
  segCache?: SegmentCache;

  constructor(
    log: (message: string) => void,
    segTranslator: SegmentTranslator,
    segCache?: SegmentCache
  ) {
    this.log = log;
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
      return this.segTranslator.version;
    } else {
      return '';
    }
  }

  async translate(
    textJp: string[],
    context?: {
      glossary?: Glossary;
      oldTextZh?: string[] | undefined;
      oldGlossary?: Glossary;
      signal?: AbortSignal;
    }
  ): Promise<string[]> {
    const oldTextZh = context?.oldTextZh;
    if (oldTextZh !== undefined && textJp.length !== oldTextZh.length) {
      throw new Error('旧翻译行数不匹配。不应当出现，请反馈给站长。');
    }

    const textZh = await emptyLineFilterWrapper(
      textJp,
      oldTextZh,
      async (textJp, oldTextZh) => {
        if (textJp.length === 0) return [];

        const resultsZh: string[][] = [];
        const segs = this.segTranslator.segmentor(textJp, oldTextZh);
        const size = segs.length;
        for (const [index, [segJp, oldSegZh]] of segs.entries()) {
          const segZh = await this.translateSeg(segJp, {
            logPrefix: `分段${index + 1}/${size}`,
            ...context,
            oldSegZh,
          });
          if (segJp.length !== segZh.length) {
            throw new Error('翻译结果行数不匹配。不应当出现，请反馈给站长。');
          }
          resultsZh.push(segZh);
        }
        return resultsZh.flat();
      }
    );
    this.segTranslator.log('完成');
    return textZh;
  }

  private async translateSeg(
    seg: string[],
    {
      logPrefix,
      glossary,
      oldSegZh,
      oldGlossary,
      signal,
    }: {
      logPrefix: string;
      glossary?: Glossary;
      oldSegZh?: string[];
      oldGlossary?: Glossary;
      signal?: AbortSignal;
    }
  ) {
    glossary = glossary || {};
    oldGlossary = oldGlossary || {};

    // 检测分段是否需要重新翻译
    const segGlossary = filterGlossary(glossary, seg);
    if (oldSegZh !== undefined) {
      const segOldGlossary = filterGlossary(oldGlossary, seg);
      if (isEqual(segGlossary, segOldGlossary)) {
        this.log(logPrefix + '　术语表无变化，无需翻译');
        return oldSegZh;
      }
    }

    // 检测是否有分段缓存存在
    let cacheKey: string | undefined;
    if (this.segCache) {
      try {
        let extra: any = { glossary };
        if (this.segTranslator instanceof SakuraTranslator) {
          extra.version = this.segTranslator.version;
          extra.fingerprint = this.segTranslator.fingerprint;
        }
        cacheKey = this.segCache.cacheKey(seg, extra);
        const cachedSegOutput = await this.segCache.get(cacheKey);
        if (cachedSegOutput && cachedSegOutput.length === seg.length) {
          this.log(logPrefix + '　从缓存恢复');
          return cachedSegOutput;
        }
      } catch (e) {
        console.error('缓存读取失败');
        console.error(e);
      }
    }

    // 翻译
    this.log(logPrefix);
    const segOutput = await this.segTranslator.translate(seg, glossary, signal);
    if (segOutput.length !== seg.length) {
      throw new Error('分段翻译结果行数不匹配，请反馈给站长');
    }

    // 保存分段缓存
    if (this.segCache && cacheKey !== undefined) {
      try {
        await this.segCache.save(cacheKey, segOutput);
      } catch (e) {
        console.error('缓存保存失败');
        console.error(e);
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
    const log = config.log;
    config.log = (message, detail) => log('　' + message, detail);
    const segTranslator = await createSegmentTranslator(config);
    let segCache: SegmentCache | undefined = undefined;
    if (cache) {
      if (config.id === 'gpt') {
        segCache = await createSegIndexedDbCache('gpt-seg-cache');
      } else if (config.id === 'sakura') {
        segCache = await createSegIndexedDbCache('sakura-seg-cache');
      }
    }
    return new Translator(log, segTranslator, segCache);
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
  textJp: string[],
  oldTextZh: string[] | undefined,
  callback: (
    textJp: string[],
    oldTextZh: string[] | undefined
  ) => Promise<string[]>
) => {
  const textJpFiltered: string[] = [];
  const oldTextZhFiltered: string[] = [];
  for (let i = 0; i < textJp.length; i++) {
    const lineJp = textJp[i].replace(/\r?\n|\r/g, '');
    if (!(lineJp.trim() === '' || lineJp.startsWith('<图片>'))) {
      textJpFiltered.push(lineJp);
      if (oldTextZh !== undefined) {
        const lineZh = oldTextZh[i];
        oldTextZhFiltered.push(lineZh);
      }
    }
  }

  const textZh = await callback(
    textJpFiltered,
    oldTextZh === undefined ? undefined : oldTextZhFiltered
  );

  const recoveredTextZh: string[] = [];
  for (const lineJp of textJp) {
    const realLineJp = lineJp.replace(/\r?\n|\r/g, '');
    if (realLineJp.trim() === '' || realLineJp.startsWith('<图片>')) {
      recoveredTextZh.push(lineJp);
    } else {
      const outputLine = textZh.shift();
      recoveredTextZh.push(outputLine!);
    }
  }
  return recoveredTextZh;
};
