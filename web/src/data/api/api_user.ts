import { runCatching } from '@/data/result';

import { WebNovelOutlineDto } from './api_web_novel';
import { WenkuNovelOutlineDto } from './api_wenku_novel';
import { client } from './client';
import { Page } from './common';

export type UserRole = 'admin' | 'maintainer' | 'trusted' | 'normal' | 'banned';

export interface UserReference {
  username: string;
  role: UserRole;
}

export interface UserOutline {
  id: string;
  email: string;
  username: boolean;
  role: UserRole;
  createAt: number;
}

const listUser = (params: { page: number; pageSize: number; role: UserRole }) =>
  runCatching(
    client.get('user', { searchParams: params }).json<Page<UserOutline>>()
  );

const listReadHistoryWeb = (searchParams: { page: number; pageSize: number }) =>
  runCatching(
    client
      .get('user/read-history', { searchParams })
      .json<Page<WebNovelOutlineDto>>()
  );

const updateReadHistoryWeb = (
  providerId: string,
  novelId: string,
  chapterId: string
) =>
  runCatching(
    client
      .put(`user/read-history/${providerId}/${novelId}`, { body: chapterId })
      .text()
  );

export interface Favored {
  id: string;
  title: string;
}
export interface FavoredList {
  web: Favored[];
  wenku: Favored[];
}
const listFavored = () =>
  runCatching(client.get('user/favored').json<FavoredList>());

const createFavoredWeb = (json: { title: string }) =>
  runCatching(client.post(`user/favored-web`, { json }).text());

const updateFavoredWeb = (favoredId: string, json: { title: string }) =>
  runCatching(client.put(`user/favored-web/${favoredId}`, { json }).text());

const deleteFavoredWeb = (favoredId: string) =>
  runCatching(client.delete(`user/favored-web/${favoredId}`).text());

const listFavoredWebNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: 'create' | 'update';
  }
) =>
  runCatching(
    client
      .get(`user/favored-web/${favoredId}`, { searchParams })
      .json<Page<WebNovelOutlineDto>>()
  );

const favoriteWebNovel = (
  favoredId: string,
  providerId: string,
  novelId: string
) =>
  runCatching(
    client.put(`user/favored-web/${favoredId}/${providerId}/${novelId}`).text()
  );

const unfavoriteWebNovel = (
  favoredId: string,
  providerId: string,
  novelId: string
) =>
  runCatching(
    client
      .delete(`user/favored-web/${favoredId}/${providerId}/${novelId}`)
      .text()
  );

const createFavoredWenku = (json: { title: string }) =>
  runCatching(client.post(`user/favored-wenku`, { json }).text());

const updateFavoredWenku = (favoredId: string, json: { title: string }) =>
  runCatching(client.put(`user/favored-wenku/${favoredId}`, { json }).text());

const deleteFavoredWenku = (favoredId: string) =>
  runCatching(client.delete(`user/favored-wenku/${favoredId}`).text());

const listFavoredWenkuNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: 'create' | 'update';
  }
) =>
  runCatching(
    client
      .get(`user/favored-wenku/${favoredId}`, { searchParams })
      .json<Page<WenkuNovelOutlineDto>>()
  );

const favoriteWenkuNovel = (favoredId: string, novelId: string) =>
  runCatching(client.put(`user/favored-wenku/${favoredId}/${novelId}`).text());

const unfavoriteWenkuNovel = (favoredId: string, novelId: string) =>
  runCatching(
    client.delete(`user/favored-wenku/${favoredId}/${novelId}`).text()
  );

export const ApiUser = {
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
