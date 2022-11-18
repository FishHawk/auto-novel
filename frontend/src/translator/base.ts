import ky from 'ky';

export abstract class Translator {
  from_lang: string;
  to_lang: string;

  constructor(from_lang: string, to_lang: string) {
    this.from_lang = from_lang;
    this.to_lang = to_lang;
  }

  abstract translate(query_list: string[]): Promise<string[]>;
}

interface MetadataToTranslate {
  metadata: string[];
  episode_ids: string[];
}

export async function get_metadata(
  provider_id: string,
  book_id: string,
  start_index: number,
  end_index: number
): Promise<MetadataToTranslate> {
  return ky
    .get(`/api/boost/metadata/${provider_id}/${book_id}`, {
      searchParams: { start_index, end_index },
    })
    .json();
}

export async function post_metadata(
  provider_id: string,
  book_id: string,
  translated: string[]
): Promise<string> {
  return ky
    .post(`/api/boost/metadata/${provider_id}/${book_id}`, {
      json: translated,
    })
    .text();
}

export async function get_episode(
  provider_id: string,
  book_id: string,
  episode_id: string
): Promise<string[]> {
  return ky
    .get(`/api/boost/episode/${provider_id}/${book_id}/${episode_id}`)
    .json();
}

export async function post_episode(
  provider_id: string,
  book_id: string,
  episode_id: string,
  translated: string[]
): Promise<string> {
  return ky
    .post(`/api/boost/episode/${provider_id}/${book_id}/${episode_id}`, {
      json: translated,
    })
    .text();
}

export async function make_book(
  provider_id: string,
  book_id: string
): Promise<string> {
  return ky.post(`/api/boost/make/${provider_id}/${book_id}`).text();
}
