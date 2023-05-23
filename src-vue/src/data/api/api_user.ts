import api from './api';
import { Result, runCatching } from './result';
import { WebNovelListPageDto } from './api_web_novel';
import { WenkuListPageDto } from './api_wenku_novel';

async function listFavoritedWebNovel(
  page: number,
  pageSize: number,
  token: string
): Promise<Result<WebNovelListPageDto>> {
  return runCatching(
    api
      .get(`user/favorited-web/list`, {
        headers: { Authorization: 'Bearer ' + token },
        searchParams: { page, pageSize },
      })
      .json()
  );
}

async function putFavoritedWebNovel(
  providerId: string,
  novelId: string,
  token: string
) {
  return runCatching(
    api
      .put(`user/favorited-web/${providerId}/${novelId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

async function deleteFavoritedWebNovel(
  providerId: string,
  novelId: string,
  token: string
) {
  return runCatching(
    api
      .delete(`user/favorited-web/${providerId}/${novelId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

async function listFavoritedWenkuNovel(
  page: number,
  pageSize: number,
  token: string
): Promise<Result<WenkuListPageDto>> {
  return runCatching(
    api
      .get(`user/favorited-wenku/list`, {
        headers: { Authorization: 'Bearer ' + token },
        searchParams: { page, pageSize },
      })
      .json()
  );
}

async function putFavoritedWenkuNovel(novelId: string, token: string) {
  return runCatching(
    api
      .put(`user/favorited-wenku/${novelId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

async function deleteFavoritedWenkuNovel(novelId: string, token: string) {
  return runCatching(
    api
      .delete(`user/favorited-wenku/${novelId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}
export default {
  listFavoritedWebNovel,
  putFavoritedWebNovel,
  deleteFavoritedWebNovel,
  listFavoritedWenkuNovel,
  putFavoritedWenkuNovel,
  deleteFavoritedWenkuNovel,
};
