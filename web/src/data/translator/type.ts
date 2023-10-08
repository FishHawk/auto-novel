export type Glossary = { [key: string]: string };

export type Segmentor = (input: string[]) => Promise<string[][]>;

export interface SegmentTranslator {
  glossary: Glossary;
  createSegments: Segmentor;
  translate: (
    seg: string[],
    segInfo: { index: number; size: number }
  ) => Promise<string[]>;
}
