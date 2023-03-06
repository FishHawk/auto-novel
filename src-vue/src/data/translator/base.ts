import { customAlphabet } from 'nanoid';

export type Glossary = { [key: string]: string };

function applyGlossary(input: string[], glossary: Glossary): string[] {
  return input.map((text) => {
    for (const wordSrc in glossary) {
      const wordDst = glossary[wordSrc];
      text = text.replace(wordSrc, wordDst);
    }
    return text;
  });
}

export abstract class Translator {
  langSrc: string;
  langDst: string;
  glossaryJpToUuid: Glossary;
  glossaryUuidToZh: Glossary;

  constructor(langSrc: string, langDst: string, glossary: Glossary) {
    this.langSrc = langSrc;
    this.langDst = langDst;

    this.glossaryJpToUuid = {};
    this.glossaryUuidToZh = {};
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

  abstract translate(textsSrc: string[]): Promise<string[]>;

  async translateWithGlossary(textsSrc: string[]): Promise<string[]> {
    const mappedTextsSrc = applyGlossary(textsSrc, this.glossaryJpToUuid);
    const textsDst = await this.translate(mappedTextsSrc);
    const unmappedTextsDst = applyGlossary(textsDst, this.glossaryUuidToZh);
    return unmappedTextsDst;
  }
}
