import { assert } from '@vueuse/shared';
import { customAlphabet } from 'nanoid';

export type Glossary = { [key: string]: string };

class GlossaryTransformer {
  glossaryJpToUuid: Glossary = {};
  glossaryUuidToZh: Glossary = {};

  constructor(glossary: Glossary) {
    const nanoid = customAlphabet('abcdefghijklmnopqrstuvwxyz', 4);

    function generateUuid() {
      while (true) {
        const uuid = nanoid();
        if (/(.)\1/.test(uuid)) return uuid;
      }
    }

    for (const wordJp in glossary) {
      const wordZh = glossary[wordJp];
      const uuid = generateUuid();
      this.glossaryJpToUuid[wordJp] = uuid;
      this.glossaryUuidToZh[uuid] = wordZh;
    }
  }

  encode(input: string[]): string[] {
    return input.map((text) => {
      for (const wordSrc in this.glossaryJpToUuid) {
        const wordDst = this.glossaryJpToUuid[wordSrc];
        text = text.replace(wordSrc, wordDst);
      }
      return text;
    });
  }

  decode(input: string[]): string[] {
    return input.map((text) => {
      for (const wordSrc in this.glossaryUuidToZh) {
        const wordDst = this.glossaryUuidToZh[wordSrc];
        text = text.replace(wordSrc, wordDst);
      }
      return text;
    });
  }
}

export class InputSegmenter {
  input: string[];
  segSizeLimit: number;

  constructor(input: string[], size: number) {
    this.input = input;
    this.segSizeLimit = size;
  }

  *segment(): Generator<string[]> {
    const seg: string[] = [];
    let segSize = 0;

    for (const line of this.input) {
      const realLine = line.replace(/\r?\n|\r/g, '');
      if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
        continue;
      }

      const lineSize = realLine.length;
      if (lineSize + segSize > this.segSizeLimit && seg.length >= 0) {
        yield seg;
        seg.length = 0;
        segSize = 0;
      }

      seg.push(realLine);
      segSize += lineSize;
    }
    if (seg.length >= 0) {
      yield seg;
    }
  }

  recover(output: string[]) {
    const recoveredOutput: string[] = [];
    for (const line of this.input) {
      const realLine = line.replace(/\r?\n|\r/g, '');
      if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
        recoveredOutput.push(line);
      } else {
        const outputLine = output.shift();
        assert(outputLine !== undefined);
        recoveredOutput.push(outputLine!);
      }
    }
    return recoveredOutput;
  }
}

export interface Translator {
  size: number;
  translate: (input: string[]) => Promise<string[]>;
}

export class TranslatorAdapter {
  private translator: Translator;
  private glossaryTransformer: GlossaryTransformer;

  constructor(translator: Translator, glossary: Glossary) {
    this.translator = translator;
    this.glossaryTransformer = new GlossaryTransformer(glossary);
  }

  private async translateInner(input: string[]): Promise<string[]> {
    const segmenter = new InputSegmenter(input, this.translator.size);

    let output: string[] = [];
    for (const seg of segmenter.segment()) {
      const segOutput = await this.translator.translate(seg);
      output = output.concat(segOutput);
    }
    const recoveredOutput = segmenter.recover(output);
    if (recoveredOutput.length != input.length) {
      throw Error('翻译长度不匹配');
    }
    return recoveredOutput;
  }

  async translate(input: string[]): Promise<string[]> {
    if (input.length === 0) return [];
    const encodedInput = this.glossaryTransformer.encode(input);
    const output = await this.translateInner(encodedInput);
    const decodedOutput = this.glossaryTransformer.decode(output);
    return decodedOutput;
  }
}
