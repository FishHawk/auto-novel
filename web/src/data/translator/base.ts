import { customAlphabet } from 'nanoid';

const filterInput = (input: string[]) =>
  input
    .map((line) => line.replace(/\r?\n|\r/g, ''))
    .filter((line) => !(line.trim() === '' || line.startsWith('<图片>')));

const recoverOutput = (input: string[], output: string[]) => {
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
  if (recoveredOutput.length !== input.length) {
    throw Error('重建翻译长度不匹配，不应当出现');
  }
  return recoveredOutput;
};

export const emptyLineFilterWrapper = async (
  input: string[],
  callback: (input: string[]) => Promise<string[]>
) => {
  const filteredInput = filterInput(input);
  const output = await callback(filteredInput);
  const recoveredOutput = recoverOutput(input, output);
  return recoveredOutput;
};

export type Glossary = { [key: string]: string };

export const createNonAiGlossaryWrapper = (glossary: Glossary) => {
  const nanoid = customAlphabet('abcdefghijklmnopqrstuvwxyz', 4);
  const generateUuid = () => {
    while (true) {
      const uuid = nanoid();
      if (/(.)\1/.test(uuid)) return uuid;
    }
  };

  const glossaryJpToUuid: Glossary = {};
  const glossaryUuidToZh: Glossary = {};
  for (const wordJp of Object.keys(glossary).sort(
    (a, b) => b.length - a.length
  )) {
    const wordZh = glossary[wordJp];
    const uuid = generateUuid();
    glossaryJpToUuid[wordJp] = uuid;
    glossaryUuidToZh[uuid] = wordZh;
  }

  const encode = (input: string[]): string[] => {
    return input.map((text) => {
      for (const wordSrc in glossaryJpToUuid) {
        const wordDst = glossaryJpToUuid[wordSrc];
        text = text.replaceAll(wordSrc, wordDst);
      }
      return text;
    });
  };

  const decode = (input: string[]): string[] => {
    return input.map((text) => {
      for (const wordSrc in glossaryUuidToZh) {
        const wordDst = glossaryUuidToZh[wordSrc];
        text = text.replaceAll(wordSrc, wordDst);
      }
      return text;
    });
  };

  return async (
    input: string[],
    callback: (input: string[]) => Promise<string[]>
  ) => {
    const encodedInput = encode(input);
    const output = await callback(encodedInput);
    const decodedOutput = decode(output);
    return decodedOutput;
  };
};

export const createLengthSegmenterWrapper = (maxLength: number) => {
  const segmenter = (input: string[]) => {
    const segs: string[][] = [];
    let seg: string[] = [];
    let segSize = 0;

    for (const line of input) {
      const lineSize = line.length;
      if (lineSize + segSize > maxLength && seg.length > 0) {
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

  return async (
    input: string[],
    callback: (
      seg: string[],
      segInfo: { index: number; size: number }
    ) => Promise<string[]>
  ) => {
    let output: string[] = [];
    const segs = segmenter(input);
    const size = segs.length;
    for (const [index, seg] of segs.entries()) {
      const segOutput = await callback(seg, { index, size });
      output = output.concat(segOutput);
    }
    return output;
  };
};

export interface Translator {
  translate: (input: string[]) => Promise<string[]>;
}
