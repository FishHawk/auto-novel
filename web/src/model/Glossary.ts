export type Glossary = { [key: string]: string };

export namespace Glossary {
  export const encodeToText = (glossary: Glossary) => {
    return JSON.stringify(glossary, null, 2);
  };

  export const decodeFromText = (text: string): Glossary | undefined => {
    try {
      const obj = JSON.parse(text);
      if (typeof obj !== 'object') return;
      const inputGlossary: { [key: string]: string } = {};
      for (const jp in obj) {
        const zh = obj[jp];
        if (typeof zh !== 'string') return;
        inputGlossary[jp] = zh;
      }
      return inputGlossary;
    } catch {
      return;
    }
  };
}
