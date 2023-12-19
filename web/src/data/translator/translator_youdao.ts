import { BaseTranslatorConfig, Glossary, SegmentTranslator } from './type';
import { createGlossaryWrapper, createLengthSegmentor } from './common';
import { Youdao } from './api/youdao';

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
    const json = await this.api.webtranslate(seg.join('\n'));
    if (json === undefined) {
      this.log(`　错误：${json}`);
      this.log('　目前有道翻译在有些环境下有问题，可能是因为广告屏蔽插件');
      throw 'quit';
    } else {
      const result = json['translateResult'].map((it: any) =>
        it.map((it: any) => it.tgt.trimEnd()).join('')
      );
      return result;
    }
  }
}
