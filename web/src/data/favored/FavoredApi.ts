import { client } from '@/data/api/client';

import { Favored } from './Favored';

interface FavoredList {
  favoredWeb: Favored[];
  favoredWenku: Favored[];
}
const listFavored = () => client.get('user/favored').json<FavoredList>();

//

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

export const FavoredApi = {
  listFavored,
  //
  createFavoredWeb,
  updateFavoredWeb,
  deleteFavoredWeb,
  favoriteWebNovel,
  unfavoriteWebNovel,
  //
  createFavoredWenku,
  updateFavoredWenku,
  deleteFavoredWenku,
  favoriteWenkuNovel,
  unfavoriteWenkuNovel,
};
