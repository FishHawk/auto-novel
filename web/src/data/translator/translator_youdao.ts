import { BaseTranslatorConfig, Glossary, SegmentTranslator } from './type';
import { createGlossaryWrapper, createLengthSegmentor } from './common';
import { Youdao } from './api/youdao';
import { safeJson } from './api/util';

export type YoudaoTranslatorConfig = BaseTranslatorConfig;

export class YoudaoTranslator implements SegmentTranslator {
  glossary: Glossary;
  log: (message: string) => void;

  private api: Youdao;

  private glossaryWarpper: ReturnType<typeof createGlossaryWrapper>;

  constructor({ client, glossary, log }: YoudaoTranslatorConfig) {
    this.glossary = glossary;
    this.log = log;

    this.api = new Youdao(client);

    this.glossaryWarpper = createGlossaryWrapper(glossary);
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

  createSegments = createLengthSegmentor(3500);

  async translate(
    seg: string[],
    _segInfo: { index: number; size: number }
  ): Promise<string[]> {
    return this.glossaryWarpper(seg, (seg) => this.translateInner(seg));
  }

  async translateInner(seg: string[]): Promise<string[]> {
    const decoded = await this.api.webtranslate(seg.join('\n'));
    const decodedJson = safeJson(decoded);

    if (decodedJson === undefined) {
      this.log(`　错误：${decoded}`);
      throw 'quit';
    } else {
      try {
        const result = decodedJson['translateResult'].map((it: any) =>
          it.map((it: any) => it.tgt.trimEnd()).join('')
        );
        return result;
      } catch (e) {
        this.log(`　错误：${decoded}`);
        throw 'quit';
      }
    }
  }
}
