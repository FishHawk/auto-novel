import ky from 'ky';
import { UpdateProgress } from './progress';

async function postMetadataUpdateTask(
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number,
  translated: boolean
): Promise<string[]> {
  return ky
    .post(`/api/update/metadata/${providerId}/${bookId}`, {
      searchParams: { startIndex, endIndex, translated },
    })
    .json();
}

async function postEpisodeUpdateTask(
  providerId: string,
  bookId: string,
  episodeId: string,
  translated: boolean
): Promise<string> {
  return ky
    .post(`/api/update/episode/${providerId}/${bookId}/${episodeId}`, {
      searchParams: { translated },
    })
    .json();
}

export async function runUpdate(
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number,
  translated: boolean,
  onProgressUpdated: (progress: UpdateProgress) => void,
  onStart: () => void,
  onFinished: (progress: UpdateProgress) => void
) {
  const progress: UpdateProgress = {
    name: '远程更新',
    total: undefined,
    error: 0,
    finished: 0,
  };
  onProgressUpdated(progress);

  console.log(`更新元数据 ${providerId}/${bookId}`);
  const episodesUntranslated = await postMetadataUpdateTask(
    providerId,
    bookId,
    startIndex,
    endIndex,
    translated
  );

  progress.total = episodesUntranslated.length;
  onProgressUpdated({
    name: '远程更新',
    total: progress.total,
    error: progress.error,
    finished: progress.finished,
  });
  onStart();

  for (const episodeId of episodesUntranslated) {
    try {
      console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
      await postEpisodeUpdateTask(providerId, bookId, episodeId, translated);

      progress.finished += 1;
      onProgressUpdated({
        name: '远程更新',
        total: progress.total,
        error: progress.error,
        finished: progress.finished,
      });
    } catch {
      progress.error += 1;
      onProgressUpdated({
        name: '远程更新',
        total: progress.total,
        error: progress.error,
        finished: progress.finished,
      });
    }
  }

  onFinished(progress);
}
