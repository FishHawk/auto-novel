import { Locator } from '@/data';
import { Glossary } from '@/model/Glossary';

import {
  BaseTranslatorConfig,
  SegmentTranslator,
  createGlossaryWrapper,
  createLengthSegmentor,
} from './common';

export class BaiduTranslator implements SegmentTranslator {
  log: (message: string) => void;
  private api = Locator.baiduRepository();

  constructor({ log }: BaiduTranslator.Config) {
    this.log = log;
  }

  async init() {
    await this.api.sug();
    return this;
  }

  segmentor = createLengthSegmentor(3500);

  async translate(
    seg: string[],
    glossary: Glossary,
    signal?: AbortSignal
  ): Promise<string[]> {
    return createGlossaryWrapper(glossary)(seg, (seg) =>
      this.translateInner(seg, signal)
    );
  }

  async translateInner(seg: string[], signal?: AbortSignal): Promise<string[]> {
    const query = seg.join('\n');
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

export namespace BaiduTranslator {
  export type Config = BaseTranslatorConfig;
  export const create = (config: Config) => new BaiduTranslator(config).init();
}
