import ky from 'ky';
import { BaiduWebTranslator } from '../translator/baidu-web';
import { UpdateProgress } from './progress';

interface MetadataToTranslate {
  metadata: string[];
  episode_ids: string[];
}

async function getBoostMetadata(
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

async function postBoostMetadata(
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

async function getBoostEpisode(
  provider_id: string,
  book_id: string,
  episode_id: string
): Promise<string[]> {
  return ky
    .get(`/api/boost/episode/${provider_id}/${book_id}/${episode_id}`)
    .json();
}

async function postBoostEpisode(
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

export async function runLocalBoost(
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number,
  onProgressUpdated: (progress: UpdateProgress) => void,
  onStart: () => void,
  onFinished: (progress: UpdateProgress) => void
) {
  const progress: UpdateProgress = {
    name: '本地加速',
    total: undefined,
    error: 0,
    finished: 0,
  };
  onProgressUpdated(progress);

  const translator = await BaiduWebTranslator.createInstance('jp', 'zh');

  console.log(`获取元数据 ${providerId}/${bookId}`);
  const metadata = await getBoostMetadata(
    providerId,
    bookId,
    startIndex,
    endIndex
  );

  console.log(`翻译元数据 ${providerId}/${bookId}`);
  const translated_metadata = await translator.translate(metadata.metadata);

  console.log(`上传元数据 ${providerId}/${bookId}`);
  await postBoostMetadata(providerId, bookId, translated_metadata);

  progress.total = metadata.episode_ids.length;
  onProgressUpdated({
    name: '本地加速',
    total: progress.total,
    error: progress.error,
    finished: progress.finished,
  });
  onStart();

  for (const episodeId of metadata.episode_ids) {
    try {
      console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
      const episode = await getBoostEpisode(providerId, bookId, episodeId);

      console.log(`翻译章节 ${providerId}/${bookId}/${episodeId}`);
      const translated_episode = await translator.translate(episode);

      console.log(`上传章节 ${providerId}/${bookId}/${episodeId}`);
      await postBoostEpisode(providerId, bookId, episodeId, translated_episode);

      progress.finished += 1;
      onProgressUpdated({
        name: '本地加速',
        total: progress.total,
        error: progress.error,
        finished: progress.finished,
      });
    } catch {
      progress.error += 1;
      onProgressUpdated({
        name: '本地加速',
        total: progress.total,
        error: progress.error,
        finished: progress.finished,
      });
    }
  }

  onFinished(progress);
}
