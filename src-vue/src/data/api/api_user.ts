import api from './api';
import { Result, runCatching } from './result';
import { BookListPageDto } from './api_web_novel';
import { WenkuListPageDto } from './api_wenku_novel';

async function listFavoritedWebBook(
  page: number,
  pageSize: number,
  token: string
): Promise<Result<BookListPageDto>> {
  return runCatching(
    api
      .get(`user/favorited-web/list`, {
        headers: { Authorization: 'Bearer ' + token },
        searchParams: { page, pageSize },
      })
      .json()
  );
}

async function putFavoritedWebBook(
  providerId: string,
  bookId: string,
  token: string
) {
  return runCatching(
    api
      .put(`user/favorited-web/${providerId}/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

async function deleteFavoritedWebBook(
  providerId: string,
  bookId: string,
  token: string
) {
  return runCatching(
    api
      .delete(`user/favorited-web/${providerId}/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

async function listFavoritedWenkuBook(
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

async function putFavoritedWenkuBook(bookId: string, token: string) {
  return runCatching(
    api
      .put(`user/favorited-wenku/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}

async function deleteFavoritedWenkuBook(bookId: string, token: string) {
  return runCatching(
    api
      .delete(`user/favorited-wenku/${bookId}`, {
        headers: { Authorization: 'Bearer ' + token },
      })
      .json()
  );
}
export default {
  listFavoritedWebBook,
  putFavoritedWebBook,
  deleteFavoritedWebBook,
  listFavoritedWenkuBook,
  putFavoritedWenkuBook,
  deleteFavoritedWenkuBook,
};
