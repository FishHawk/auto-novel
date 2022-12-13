import ky from 'ky';

interface MetadataToTranslate {
  metadata: string[];
  episode_ids: string[];
}

export async function getBoostMetadata(
  providerId: string,
  bookId: string,
  start_index: number,
  end_index: number
): Promise<MetadataToTranslate> {
  return ky
    .get(`/api/boost/metadata/${providerId}/${bookId}`, {
      searchParams: { start_index, end_index },
    })
    .json();
}

export async function postBoostMetadata(
  providerId: string,
  bookId: string,
  translated: string[]
): Promise<string> {
  return ky
    .post(`/api/boost/metadata/${providerId}/${bookId}`, {
      json: translated,
    })
    .text();
}

export async function getBoostEpisode(
  provider_id: string,
  book_id: string,
  episode_id: string
): Promise<string[]> {
  return ky
    .get(`/api/boost/episode/${provider_id}/${book_id}/${episode_id}`)
    .json();
}

export async function postBoostEpisode(
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

export async function postBoostMakeBook(
  provider_id: string,
  book_id: string
): Promise<string> {
  return ky.post(`/api/boost/make/${provider_id}/${book_id}`).text();
}
