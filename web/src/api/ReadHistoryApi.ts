import type { Page } from '@/model/Page';
import type { WebNovelOutlineDto } from '@/model/WebNovel';
import { client } from './client';

const listReadHistoryWeb = (searchParams: { page: number; pageSize: number }) =>
  client
    .get('user/read-history', { searchParams })
    .json<Page<WebNovelOutlineDto>>();

const clearReadHistoryWeb = () => client.delete('user/read-history');

const updateReadHistoryWeb = (
  providerId: string,
  novelId: string,
  chapterId: string,
) =>
  client.put(`user/read-history/${providerId}/${novelId}`, { body: chapterId });

const deleteReadHistoryWeb = (providerId: string, novelId: string) =>
  client.delete(`user/read-history/${providerId}/${novelId}`);

//

const isReadHistoryPaused = () =>
  client.get('user/read-history/paused').json<boolean>();
const pauseReadHistory = () => client.put('user/read-history/paused');
const resumeReadHistory = () => client.delete('user/read-history/paused');

export const ReadHistoryApi = {
  listReadHistoryWeb,
  clearReadHistoryWeb,
  updateReadHistoryWeb,
  deleteReadHistoryWeb,
  //
  isReadHistoryPaused,
  pauseReadHistory,
  resumeReadHistory,
};
