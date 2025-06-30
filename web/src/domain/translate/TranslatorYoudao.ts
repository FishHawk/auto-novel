import { Locator } from '@/data';
import { RegexUtil, safeJson } from '@/util';

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
      await this.api.rlog();
      await this.api.refreshKey();
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
    let from = 'auto';
    const segText = seg.join('\n');
    if (RegexUtil.hasHangulChars(segText)) {
      from = 'ko';
    } else if (RegexUtil.hasKanaChars(segText) || RegexUtil.hasHanzi(segText)) {
      from = 'ja';
    } else if (RegexUtil.hasEnglishChars(segText)) {
      from = 'en';
    }

    const decoded = await this.api.webtranslate(seg.join('\n'), from, {
      signal,
    });
    const decodedJson = safeJson<{ translateResult: { tgt: string }[][] }>(
      decoded,
    );

    if (decodedJson === undefined) {
      this.log(`　错误：${decoded}`);
      throw 'quit';
    } else {
      try {
        const result = decodedJson['translateResult'].map((it) =>
          it.map((it) => it.tgt.trimEnd()).join(''),
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
