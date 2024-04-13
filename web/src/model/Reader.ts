export interface ReaderTocItem {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
}

export interface ReaderChapter {
  titleJp: string;
  titleZh?: string;
  prevId?: string;
  nextId?: string;
  paragraphs: string[];
  baiduParagraphs?: string[];
  youdaoParagraphs?: string[];
  gptParagraphs?: string[];
  sakuraParagraphs?: string[];
}

export type ReaderParagraph =
  | { text: string; secondary: boolean; needSpeak: boolean, popover?: number }
  | { imageUrl: string }
  | null;
