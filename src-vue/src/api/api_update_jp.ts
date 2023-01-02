import ky from 'ky';
import { Ref } from 'vue';
import { UpdateProgress } from './progress';
import { Result, Ok, Err } from './result';

async function postMetadata(
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number
): Promise<Result<string[]>> {
  return ky
    .post(`/api/update-jp/metadata/${providerId}/${bookId}`, {
      searchParams: { startIndex, endIndex },
    })
    .json<string[]>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function postEpisode(
  providerId: string,
  bookId: string,
  episodeId: string
): Promise<Result<string>> {
  return ky
    .post(`/api/update-jp/episode/${providerId}/${bookId}/${episodeId}`)
    .text()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export async function updateJp(
  progress: Ref<UpdateProgress | undefined>,
  providerId: string,
  bookId: string,
  startIndex: number,
  endIndex: number,
  onStart: () => void
): Promise<Result<UpdateProgress, any>> {
  const name = '更新日文';
  let total = undefined;
  let finished = 0;
  let error = 0;

  progress.value = { name, total, finished, error };

  console.log(`更新元数据 ${providerId}/${bookId}`);
  const episodeIdsResult = await postMetadata(
    providerId,
    bookId,
    startIndex,
    endIndex
  );
  if (!episodeIdsResult.ok) {
    return Err(episodeIdsResult.error);
  }
  const episodeIds = episodeIdsResult.value;

  total = episodeIds.length;
  progress.value = { name, total, finished, error };

  onStart();

  for (const episodeId of episodeIds) {
    console.log(`获取章节 ${providerId}/${bookId}/${episodeId}`);
    const result = await postEpisode(providerId, bookId, episodeId);
    if (result.ok) {
      finished += 1;
    } else {
      error += 1;
    }
    progress.value = { name, total, finished, error };
  }

  return Ok({ name, total, finished, error });
}
