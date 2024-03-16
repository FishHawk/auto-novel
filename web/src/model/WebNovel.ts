import { Favored } from "./User";

export interface WebNovelOutlineDto {
  providerId: string;
  novelId: string;
  titleJp: string;
  titleZh?: string;
  type: string;
  attentions: string[];
  keywords: string[];
  extra?: string;
  total: number;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
  updateAt?: number;
}

export interface WebNovelTocItemDto {
  titleJp: string;
  titleZh?: string;
  chapterId?: string;
  createAt?: number;
}

export interface WebNovelDto {
  wenkuId?: string;
  titleJp: string;
  titleZh?: string;
  authors: { name: string; link: string }[];
  type: string;
  attentions: string[];
  keywords: string[];
  points?: number;
  totalCharacters?: number;
  introductionJp: string;
  introductionZh?: string;
  glossary: { [key: string]: string };
  toc: WebNovelTocItemDto[];
  visited: number;
  syncAt: number;
  favored?: string;
  favoredList: Favored[];
  lastReadChapterId?: string;
  jp: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
}

export interface WebNovelChapterDto {
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
