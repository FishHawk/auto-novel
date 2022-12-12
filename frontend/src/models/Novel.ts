import ky from 'ky';

export type Result<T, E = undefined> =
  | { ok: true; value: T }
  | { ok: false; error: E | undefined };
const Ok = <T>(data: T): Result<T, never> => {
  return { ok: true, value: data };
};
const Err = <E>(error?: E): Result<never, E> => {
  return { ok: false, error };
};

export interface Author {
  name: string;
  link: string;
}

export interface TocEpisodeToken {
  title: string;
  episode_id: string;
}

export interface TocChapterToken {
  title: string;
  level: number;
}

export interface BookMetadata {
  title: string;
  authors: Author[];
  introduction: string;
  toc: (TocEpisodeToken | TocChapterToken)[];
}

export interface NovelMetadata {
  url: string;
  jp: BookMetadata;
  zh: BookMetadata | null;
}

export interface NovelEpisode {
  jp: string[];
  zh: string[] | null;
}

export async function getNovelMetadata(
  providerId: string,
  bookId: string
): Promise<Result<NovelMetadata, any>> {
  return ky
    .get(`/api/novel/metadata/${providerId}/${bookId}`)
    .json<NovelMetadata>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export async function getNovelEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<Result<NovelEpisode, any>> {
  return ky
    .get(`/api/novel/episode/${providerId}/${bookId}/${episodeId}`)
    .json<NovelEpisode>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}
