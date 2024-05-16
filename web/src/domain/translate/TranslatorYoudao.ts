import { Locator } from '@/data';
import { safeJson } from '@/util';

import {
  Logger,
  SegmentContext,
  SegmentTranslator,
  createGlossaryWrapper,
  createLengthSegmentor,
} from './Common';

export class YoudaoTranslator implements SegmentTranslator {
  id = <const>'youdao';
  log: Logger;
  private api = Locator.youdaoRepository();

  constructor(log: Logger) {
    this.log = log;
  }

  async init() {
    try {
      this.api.rlog();
      this.api.refreshKey();
    } catch (e) {
      this.log('无法获得Key，使用默认值');
    }
    return this;
  }

  segmentor = createLengthSegmentor(3500);

  async translate(
    seg: string[],
    { glossary, signal }: SegmentContext,
  ): Promise<string[]> {
    return createGlossaryWrapper(glossary)(seg, (seg) =>
      this.translateInner(seg, signal),
    );
  }

  async translateInner(seg: string[], signal?: AbortSignal): Promise<string[]> {
    const decoded = await this.api.webtranslate(seg.join('\n'), { signal });
    const decodedJson = safeJson<any>(decoded);

    if (decodedJson === undefined) {
      this.log(`　错误：${decoded}`);
      throw 'quit';
    } else {
      try {
        const result = decodedJson['translateResult'].map((it: any) =>
          it.map((it: any) => it.tgt.trimEnd()).join(''),
        );
        return result;
      } catch (e) {
        this.log(`　错误：${decoded}`);
        throw 'quit';
      }
    }
  }
}

export namespace YoudaoTranslator {
  export const create = (log: Logger) => new YoudaoTranslator(log).init();
}
