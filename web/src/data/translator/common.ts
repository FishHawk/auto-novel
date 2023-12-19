import { customAlphabet } from 'nanoid';

import { Glossary, Segmentor } from './type';

export const createGlossaryWrapper = (glossary: Glossary) => {
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

export const createLengthSegmentor =
  (maxLength: number): Segmentor =>
  async (input: string[]) => {
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
