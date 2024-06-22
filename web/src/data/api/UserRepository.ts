import { Page } from '@/model/Page';
import { UserOutline, UserRole } from '@/model/User';
import { WebNovelOutlineDto } from '@/model/WebNovel';
import { WenkuNovelOutlineDto } from '@/model/WenkuNovel';

import { client } from './client';

const listUser = (params: { page: number; pageSize: number; role: UserRole }) =>
  client.get('user', { searchParams: params }).json<Page<UserOutline>>();

const listReadHistoryWeb = (searchParams: { page: number; pageSize: number }) =>
  client
    .get('user/read-history', { searchParams })
    .json<Page<WebNovelOutlineDto>>();

const updateReadHistoryWeb = (
  providerId: string,
  novelId: string,
  chapterId: string,
) =>
  client.put(`user/read-history/${providerId}/${novelId}`, { body: chapterId });

const deleteReadHistoryWeb = (providerId: string, novelId: string) =>
  client.delete(`user/read-history/${providerId}/${novelId}`);

const listFavoredWebNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: 'create' | 'update';
  },
) =>
  client
    .get(`user/favored-web/${favoredId}`, { searchParams })
    .json<Page<WebNovelOutlineDto>>();

const listFavoredWenkuNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: 'create' | 'update';
  },
) =>
  client
    .get(`user/favored-wenku/${favoredId}`, { searchParams })
    .json<Page<WenkuNovelOutlineDto>>();

export const UserRepository = {
  listUser,
  //
  listReadHistoryWeb,
  updateReadHistoryWeb,
  deleteReadHistoryWeb,
  //
  listFavoredWebNovel,
  listFavoredWenkuNovel,
};
