import { Glossary } from '@/data/translator/type';

export interface ChapterTranslation {
  glossaryId: string;
  glossary: Glossary;
  paragraphs: string[];
}

export interface LocalVolumeMetadata {
  id: string;
  createAt: number;
  toc: {
    chapterId: string;
    baidu?: string;
    youdao?: string;
    gpt?: string;
    sakura?: string;
  }[];
  glossaryId: string;
  glossary: Glossary;
}

export interface LocalVolumeChapter {
  id: string;
  volumeId: string;
  paragraphs: string[];
  baidu?: ChapterTranslation;
  youdao?: ChapterTranslation;
  gpt?: ChapterTranslation;
  sakura?: ChapterTranslation;
}
