import { api } from './api';
import { runCatching } from './result';
import { WenkuNovelOutlineDto } from './api_wenku_novel';
import { WebNovelOutlineDto } from './api_web_novel';
import { Page } from './page';

const listReadHistoryWebNovel = (page: number, pageSize: number) =>
  runCatching(
    api
      .get(`user/read-history-web/list`, { searchParams: { page, pageSize } })
      .json<Page<WebNovelOutlineDto>>()
  );

const putReadHistoryWebNovel = (
  providerId: string,
  novelId: string,
  chapterId: string
) =>
  runCatching(
    api
      .put(`user/read-history-web/${providerId}/${novelId}`, {
        body: chapterId,
      })
      .text()
  );

const listFavoritedWebNovel = (
  page: number,
  pageSize: number,
  sort: 'create' | 'update'
) =>
  runCatching(
    api
      .get(`user/favorited-web/list`, {
        searchParams: { page, pageSize, sort },
      })
      .json<Page<WebNovelOutlineDto>>()
  );

const putFavoritedWebNovel = (providerId: string, novelId: string) =>
  runCatching(api.put(`user/favorited-web/${providerId}/${novelId}`).text());

const deleteFavoritedWebNovel = (providerId: string, novelId: string) =>
  runCatching(api.delete(`user/favorited-web/${providerId}/${novelId}`).text());

const listFavoritedWenkuNovel = (
  page: number,
  pageSize: number,
  sort: 'create' | 'update'
) =>
  runCatching(
    api
      .get(`user/favorited-wenku/list`, {
        searchParams: { page, pageSize, sort },
      })
      .json<Page<WenkuNovelOutlineDto>>()
  );

const putFavoritedWenkuNovel = (novelId: string) =>
  runCatching(api.put(`user/favorited-wenku/${novelId}`).text());

const deleteFavoritedWenkuNovel = (novelId: string) =>
  runCatching(api.delete(`user/favorited-wenku/${novelId}`).text());

export const ApiUser = {
  listReadHistoryWebNovel,
  putReadHistoryWebNovel,
  //
  listFavoritedWebNovel,
  putFavoritedWebNovel,
  deleteFavoritedWebNovel,
  //
  listFavoritedWenkuNovel,
  putFavoritedWenkuNovel,
  deleteFavoritedWenkuNovel,
};
