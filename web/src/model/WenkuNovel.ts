import { Favored } from './User';

export interface WenkuNovelOutlineDto {
  id: string;
  title: string;
  titleZh: string;
  cover: string;
}

export interface WenkuNovelDto {
  title: string;
  titleZh: string;
  cover: string;
  authors: string[];
  artists: string[];
  keywords: string[];
  r18: boolean;
  introduction: string;
  webIds: string[];
  volumes: WenkuVolumeDto[];
  glossary: { [key: string]: string };
  visited: number;
  favored?: string;
  favoredList: Favored[];
  volumeZh: string[];
  volumeJp: VolumeJpDto[];
}

export interface WenkuVolumeDto {
  asin: string;
  title: string;
  titleZh?: string;
  cover: string;
}

export interface VolumeJpDto {
  volumeId: string;
  total: number;
  baidu: number;
  youdao: number;
  gpt: number;
  sakura: number;
}
