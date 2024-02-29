export type Glossary = { [key: string]: string };

export type Segmentor = (input: string[]) => Promise<string[][]>;

export interface BaseTranslatorConfig {
  log: (message: string, detail?: string[]) => void;
}

export interface SegmentTranslator {
  createSegments: Segmentor;
  translate: (
    seg: string[],
    segInfo: { index: number; size: number },
    glossary: Glossary
  ) => Promise<string[]>;
  log: (message: string, detail?: string[]) => void;
}
