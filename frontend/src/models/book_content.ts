import ky from 'ky';
import { Result, Ok, Err } from './util';

interface Author {
  name: string;
  link: string;
}

interface RawTocEpisodeToken {
  title: string;
  episode_id: string;
}

interface RawTocChapterToken {
  title: string;
  level: number;
}

interface RawBookMetadata {
  title: string;
  authors: Author[];
  introduction: string;
  toc: (RawTocEpisodeToken | RawTocChapterToken)[];
}

interface RawContentMetadata {
  jp: RawBookMetadata;
  zh: RawBookMetadata | null;
}

export interface TocEpisodeToken {
  title: string;
  zh_title: string | undefined;
  episode_id: string;
}

export interface TocChapterToken {
  title: string;
  zh_title: string | undefined;
  level: number;
}

export interface ContentMetadata {
  authors: Author[];
  title: string;
  zh_title: string | undefined;
  introduction: string;
  zh_introduction: string | undefined;
  toc: (TocEpisodeToken | TocChapterToken)[];
}
export async function getContentMetadata(
  providerId: string,
  bookId: string
): Promise<Result<ContentMetadata, any>> {
  return ky
    .get(`/api/content/metadata/${providerId}/${bookId}`)
    .json<RawContentMetadata>()
    .then((it) => {
      const toc: (TocEpisodeToken | TocChapterToken)[] = [];
      for (let i = 0; i < it.jp.toc.length; i++) {
        const token = it.jp.toc[i] as any;
        const zh_token = it.zh?.toc[i];
        if (zh_token !== undefined) {
          token.zh_title = zh_token.title;
        }
        toc.push(token);
      }
      return Ok({
        authors: it.jp.authors,
        title: it.jp.title,
        zh_title: it.zh?.title,
        introduction: it.jp.introduction,
        zh_introduction: it.zh?.introduction,
        toc: toc,
      });
    })
    .catch((error) => Err(error));
}

interface RawContentEpisode {
  curr: TocEpisodeToken;
  prev: TocEpisodeToken | null;
  next: TocEpisodeToken | null;
  jp: string[];
  zh: string[] | null;
}

interface Paragraph {
  jp: string;
  zh: string | undefined;
}

export interface ContentEpisode {
  curr: TocEpisodeToken;
  prev: TocEpisodeToken | null;
  next: TocEpisodeToken | null;
  paragraphs: Paragraph[];
  translated: boolean;
}

export async function getContentEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<Result<ContentEpisode, any>> {
  return ky
    .get(`/api/content/episode/${providerId}/${bookId}/${episodeId}`)
    .json<RawContentEpisode>()
    .then((it) => {
      const paragraphs: Paragraph[] = [];
      if (it.zh === null) {
        it.jp.forEach((text) => {
          paragraphs.push({ jp: text, zh: undefined });
        });
      } else {
        if (it.zh.length === it.jp.length) {
          for (let i = 0; i < it.zh.length; i++) {
            paragraphs.push({ jp: it.jp[i], zh: it.zh[i] });
          }
        } else {
          return Err('翻译失效');
        }
      }
      return Ok({
        prev: it.prev,
        next: it.next,
        curr: it.curr,
        paragraphs,
        translated: it.zh !== null,
      });
    })
    .catch((error) => Err(error));
}
