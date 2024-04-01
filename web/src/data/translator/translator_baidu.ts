import { Glossary } from '@/model/Glossary';

import { Baidu } from './api/baidu';
import { createGlossaryWrapper, createLengthSegmentor } from './common';
import { BaseTranslatorConfig, SegmentTranslator } from './type';

export type BaiduTranslatorConfig = BaseTranslatorConfig;

export class BaiduTranslator implements SegmentTranslator {
  log: (message: string) => void;
  private api = new Baidu();

  constructor({ log }: BaiduTranslatorConfig) {
    this.log = log;
  }

  async init() {
    await this.api.sug();
    return this;
  }

  createSegments = createLengthSegmentor(3500);

  async translate(
    seg: string[],
    _segInfo: { index: number; size: number },
    glossary: Glossary,
    signal?: AbortSignal
  ): Promise<string[]> {
    return createGlossaryWrapper(glossary)(seg, (seg) =>
      this.translateInner(seg, signal)
    );
  }

  async translateInner(
    input: string[],
    signal?: AbortSignal
  ): Promise<string[]> {
    // 开头的空格似乎会导致998错误
    const newInput = input.slice();
    newInput[0] = newInput[0].trimStart();
    const query = newInput.join('\n');

    const chunks = await this.api.translate(query, { signal });

    const lines: string[][] = [];
    Array.from(chunks).forEach((chunk) => {
      if (chunk.data.event === 'Translating') {
        lines.push(chunk.data.list.map((it) => it.dst));
      }
    });

    return lines.flat();
  }
}
