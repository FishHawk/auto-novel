import type { Page } from '@/model/Page';
import type { WebNovelOutlineDto } from '@/model/WebNovel';
import type { WenkuNovelOutlineDto } from '@/model/WenkuNovel';
import { client } from './client';

export interface Favored {
  id: string;
  title: string;
}

interface FavoredList {
  favoredWeb: Favored[];
  favoredWenku: Favored[];
}
const listFavored = () => client.get('user/favored').json<FavoredList>();

//

const listFavoredWebNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    query?: string;
    provider?: string;
    type?: number;
    level?: number;
    translate?: number;
    sort: string;
  },
) =>
  client
    .get(`user/favored-web/${favoredId}`, { searchParams })
    .json<Page<WebNovelOutlineDto>>();

const createFavoredWeb = (json: { title: string }) =>
  client.post(`user/favored-web`, { json }).text();

const updateFavoredWeb = (favoredId: string, json: { title: string }) =>
  client.put(`user/favored-web/${favoredId}`, { json });

const deleteFavoredWeb = (favoredId: string) =>
  client.delete(`user/favored-web/${favoredId}`);

const favoriteWebNovel = (
  favoredId: string,
  providerId: string,
  novelId: string,
) => client.put(`user/favored-web/${favoredId}/${providerId}/${novelId}`);

const unfavoriteWebNovel = (
  favoredId: string,
  providerId: string,
  novelId: string,
) => client.delete(`user/favored-web/${favoredId}/${providerId}/${novelId}`);

//

const listFavoredWenkuNovel = (
  favoredId: string,
  searchParams: {
    page: number;
    pageSize: number;
    sort: string;
  },
) =>
  client
    .get(`user/favored-wenku/${favoredId}`, { searchParams })
    .json<Page<WenkuNovelOutlineDto>>();

const createFavoredWenku = (json: { title: string }) =>
  client.post(`user/favored-wenku`, { json }).text();

const updateFavoredWenku = (favoredId: string, json: { title: string }) =>
  client.put(`user/favored-wenku/${favoredId}`, { json });

const deleteFavoredWenku = (favoredId: string) =>
  client.delete(`user/favored-wenku/${favoredId}`);

const favoriteWenkuNovel = (favoredId: string, novelId: string) =>
  client.put(`user/favored-wenku/${favoredId}/${novelId}`);

const unfavoriteWenkuNovel = (favoredId: string, novelId: string) =>
  client.delete(`user/favored-wenku/${favoredId}/${novelId}`);

//

export const FavoredApi = {
  listFavored,
  //
  listFavoredWebNovel,
  createFavoredWeb,
  updateFavoredWeb,
  deleteFavoredWeb,
  favoriteWebNovel,
  unfavoriteWebNovel,
  //
  listFavoredWenkuNovel,
  createFavoredWenku,
  updateFavoredWenku,
  deleteFavoredWenku,
  favoriteWenkuNovel,
  unfavoriteWenkuNovel,
};
