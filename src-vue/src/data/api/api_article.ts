import { api } from './api';
import { Page } from './page';
import { runCatching } from './result';

export type UserRole = 'admin' | 'maintainer' | 'normal';

interface UserOutline {
  username: string;
  role: UserRole;
}

export interface ArticleOutline {
  id: string;
  title: string;
  locked: boolean;
  pinned: boolean;
  numViews: number;
  numComments: number;
  user: UserOutline;
  createAt: number;
  updateAt: number;
}

export interface Article {
  id: string;
  title: string;
  content: string;
  locked: boolean;
  pinned: boolean;
  numViews: number;
  numComments: number;
  user: UserOutline;
  createAt: number;
  updateAt: number;
}

const listArticle = (page: number) =>
  runCatching(
    api.get('article', { searchParams: { page } }).json<Page<ArticleOutline>>()
  );

const getArticle = (id: string) =>
  runCatching(api.get(`article/${id}`).json<Article>());

const createArticle = (json: { title: string; content: string }) =>
  runCatching(api.post('article', { json }).text());

const updateArticle = (id: string, json: { title: string; content: string }) =>
  runCatching(api.put(`article/${id}`, { json }).text());

const deleteArticle = (id: string) =>
  runCatching(api.delete(`article/${id}`).text());

const pinArticle = (id: string) =>
  runCatching(api.put(`article/${id}/pinned`).text());

const unpinArticle = (id: string) =>
  runCatching(api.delete(`article/${id}/pinned`).text());

const lockArticle = (id: string) =>
  runCatching(api.put(`article/${id}/locked`).text());

const unlockArticle = (id: string) =>
  runCatching(api.delete(`article/${id}/locked`).text());

export const ApiArticle = {
  listArticle,
  getArticle,
  createArticle,
  updateArticle,
  deleteArticle,
  pinArticle,
  unpinArticle,
  lockArticle,
  unlockArticle,
};
