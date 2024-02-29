export type Glossary = { [key: string]: string };

export type Segmentor = (input: string[]) => string[][];

export interface BaseTranslatorConfig {
  log: (message: string, detail?: string[]) => void;
}

export interface SegmentTranslator {
  createSegments: Segmentor;
  translate: (
    seg: string[],
    segInfo: { index: number; size: number },
    glossary: Glossary,
    signal?: AbortSignal
  ) => Promise<string[]>;
  log: (message: string, detail?: string[]) => void;
}
