import { customAlphabet } from 'nanoid';
import { Segmenter } from './util';

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
        text = text.replaceAll(wordSrc, wordDst);
      }
      return text;
    });
  }

  decode(input: string[]): string[] {
    return input.map((text) => {
      for (const wordSrc in this.glossaryUuidToZh) {
        const wordDst = this.glossaryUuidToZh[wordSrc];
        text = text.replaceAll(wordSrc, wordDst);
      }
      return text;
    });
  }
}

export abstract class Translator {
  abstract createSegmenter(input: string[]): Segmenter;
  abstract translateSegment(input: string[]): Promise<string[]>;

  async translate(input: string[]): Promise<string[]> {
    const segmenter = this.createSegmenter(input);
    let output: string[] = [];
    for (const seg of segmenter.segment()) {
      const segOutput = await this.translateSegment(seg);
      output = output.concat(segOutput);
    }
    const recoveredOutput = segmenter.recover(output);
    if (recoveredOutput.length != input.length) {
      throw Error('重建翻译长度不匹配，不应当出现');
    }
    return recoveredOutput;
  }
}

export class TranslatorAdapter {
  private translator: Translator;
  private glossaryTransformer: GlossaryTransformer;

  constructor(translator: Translator, glossary: Glossary) {
    this.translator = translator;
    this.glossaryTransformer = new GlossaryTransformer(glossary);
  }

  async translate(input: string[]): Promise<string[]> {
    if (input.length === 0) return [];
    const encodedInput = this.glossaryTransformer.encode(input);
    const output = await this.translator.translate(encodedInput);
    const decodedOutput = this.glossaryTransformer.decode(output);
    return decodedOutput;
  }
}
