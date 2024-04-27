import { Glossary } from '@/model/Glossary';

import { Locator } from '@/data';
import { createGlossaryWrapper, createLengthSegmentor } from './common';
import { BaseTranslatorConfig, SegmentTranslator } from './type';

export type BaiduTranslatorConfig = BaseTranslatorConfig;

export class BaiduTranslator implements SegmentTranslator {
  log: (message: string) => void;
  private api = Locator.baiduRepository();

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
    const query = input.join('\n');
    const chunks = await this.api.translate(query, { signal });

    const lineParts: { paraIdx: number; dst: string }[] = [];
    Array.from(chunks).forEach((chunk) => {
      if (chunk.data.event === 'Translating') {
        lineParts.push(...chunk.data.list);
      }
    });

    const lines: string[] = [];
    let currentParaIdx = 0;
    let currentLine = '';
    lineParts.forEach(({ paraIdx, dst }) => {
      if (paraIdx === currentParaIdx) {
        currentLine = currentLine + dst;
      } else {
        lines.push(currentLine);
        currentParaIdx = paraIdx;
        currentLine = dst;
      }
    });
    lines.push(currentLine);

    return lines;
  }
}
