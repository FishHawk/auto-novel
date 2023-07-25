import { customAlphabet } from 'nanoid';
import { get_encoding } from 'tiktoken';

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

function filterInput(input: string[]) {
  return input
    .map((line) => line.replace(/\r?\n|\r/g, ''))
    .filter((line) => !(line.trim() === '' || line.startsWith('<图片>')));
}

function recoverOutput(input: string[], output: string[]) {
  const recoveredOutput: string[] = [];
  for (const line of input) {
    const realLine = line.replace(/\r?\n|\r/g, '');
    if (realLine.trim() === '' || realLine.startsWith('<图片>')) {
      recoveredOutput.push(line);
    } else {
      const outputLine = output.shift();
      recoveredOutput.push(outputLine!);
    }
  }
  return recoveredOutput;
}

type Segmenter = (input: string[]) => string[][];

export function tokenSegmenter(maxToken: number, maxLine: number): Segmenter {
  return (input: string[]) => {
    const segs: string[][] = [];
    let seg: string[] = [];
    let segSize = 0;

    const encoder = get_encoding('p50k_base');
    for (const line of input) {
      const lineSize = encoder.encode(line).length;
      if (segSize + lineSize > maxToken || seg.length >= maxLine) {
        segs.push(seg);
        seg = [line];
        segSize = lineSize;
      } else {
        seg.push(line);
        segSize += lineSize;
      }
    }

    if (seg.length > 0) {
      segs.push(seg);
    }

    // 如果最后的分段过小，与上一个分段合并。
    if (segs.length >= 2) {
      const last1Seg = segs[segs.length - 1];
      const last1TokenSize = last1Seg.reduce(
        (a, b) => a + encoder.encode(b).length,
        0
      );
      if (last1Seg.length <= 5 && last1TokenSize <= 500) {
        const last2Seg = segs[segs.length - 2];
        last2Seg.push(...last1Seg);
        segs.pop();
      }
    }

    encoder.free();
    return segs;
  };
}

export function lengthSegmenter(maxLength: number): Segmenter {
  return (input: string[]) => {
    const segs: string[][] = [];
    let seg: string[] = [];
    let segSize = 0;

    for (const line of input) {
      const lineSize = line.length;
      if (lineSize + segSize > maxLength && seg.length >= 0) {
        segs.push(seg);
        seg = [line];
        segSize = lineSize;
      } else {
        seg.push(line);
        segSize += lineSize;
      }
    }
    if (seg.length > 0) {
      segs.push(seg);
    }
    return segs;
  };
}

export abstract class Translator {
  private glossaryTransformer: GlossaryTransformer;
  protected log: (message: string) => void;
  abstract segmenter: Segmenter;

  constructor(glossary: Glossary, log?: (message: string) => void) {
    this.glossaryTransformer = new GlossaryTransformer(glossary);
    this.log = log ?? console.log;
  }

  abstract translateSegment(
    seg: string[],
    segInfo: { index: number; size: number }
  ): Promise<string[]>;

  async translate(input: string[]): Promise<string[]> {
    if (input.length === 0) return [];
    const encodedInput = this.glossaryTransformer.encode(input);
    const filteredInput = filterInput(encodedInput);
    const output = await this.translateInner(filteredInput);
    const recoveredOutput = recoverOutput(encodedInput, output);
    if (recoveredOutput.length !== encodedInput.length) {
      throw Error('重建翻译长度不匹配，不应当出现');
    }
    const decodedOutput = this.glossaryTransformer.decode(recoveredOutput);
    return decodedOutput;
  }

  private async translateInner(input: string[]): Promise<string[]> {
    let output: string[] = [];
    const segs = this.segmenter(input);
    const size = segs.length;
    for (const [index, seg] of segs.entries()) {
      const segOutput = await this.translateSegment(seg, { index, size });
      output = output.concat(segOutput);
    }
    return output;
  }
}
