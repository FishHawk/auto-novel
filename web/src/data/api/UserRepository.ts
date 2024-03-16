import { Page } from '@/model/Page';
import { FavoredList, UserOutline, UserRole } from '@/model/User';
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
  chapterId: string
) =>
  client.put(`user/read-history/${providerId}/${novelId}`, { body: chapterId });

const listFavored = () => client.get('user/favored').json<FavoredList>();

const createFavoredWeb = (json: { title: string }) =>
  client.post(`user/favored-web`, { json });

const updateFavoredWeb = (favoredId: string, json: { title: string }) =>
  client.put(`user/favored-web/${favoredId}`, { json });

const deleteFavoredWeb = (favoredId: string) =>
  client.delete(`user/favored-web/${favoredId}`);

const listFavoredWebNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: 'create' | 'update';
  }
) =>
  client
    .get(`user/favored-web/${favoredId}`, { searchParams })
    .json<Page<WebNovelOutlineDto>>();

const favoriteWebNovel = (
  favoredId: string,
  providerId: string,
  novelId: string
) => client.put(`user/favored-web/${favoredId}/${providerId}/${novelId}`);

const unfavoriteWebNovel = (
  favoredId: string,
  providerId: string,
  novelId: string
) => client.delete(`user/favored-web/${favoredId}/${providerId}/${novelId}`);

const createFavoredWenku = (json: { title: string }) =>
  client.post(`user/favored-wenku`, { json });

const updateFavoredWenku = (favoredId: string, json: { title: string }) =>
  client.put(`user/favored-wenku/${favoredId}`, { json });

const deleteFavoredWenku = (favoredId: string) =>
  client.delete(`user/favored-wenku/${favoredId}`);

const listFavoredWenkuNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: 'create' | 'update';
  }
) =>
  client
    .get(`user/favored-wenku/${favoredId}`, { searchParams })
    .json<Page<WenkuNovelOutlineDto>>();

const favoriteWenkuNovel = (favoredId: string, novelId: string) =>
  client.put(`user/favored-wenku/${favoredId}/${novelId}`);

const unfavoriteWenkuNovel = (favoredId: string, novelId: string) =>
  client.delete(`user/favored-wenku/${favoredId}/${novelId}`);

export const UserRepository = {
  listUser,
  //
  listReadHistoryWeb,
  updateReadHistoryWeb,
  //
  listFavored,
  //
  createFavoredWeb,
  updateFavoredWeb,
  deleteFavoredWeb,
  listFavoredWebNovel,
  favoriteWebNovel,
  unfavoriteWebNovel,
  //
  createFavoredWenku,
  updateFavoredWenku,
  deleteFavoredWenku,
  listFavoredWenkuNovel,
  favoriteWenkuNovel,
  unfavoriteWenkuNovel,
};
