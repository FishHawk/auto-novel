import { runCatching } from '@/data/result';

import { client } from './client';
import { Page, UserOutline } from './common';

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

const listArticle = (params: { page: number; pageSize: number }) =>
  runCatching(
    client.get('article', { searchParams: params }).json<Page<ArticleOutline>>()
  );

const getArticle = (id: string) =>
  runCatching(client.get(`article/${id}`).json<Article>());

const createArticle = (json: { title: string; content: string }) =>
  runCatching(client.post('article', { json }).text());

const updateArticle = (id: string, json: { title: string; content: string }) =>
  runCatching(client.put(`article/${id}`, { json }).text());

const deleteArticle = (id: string) =>
  runCatching(client.delete(`article/${id}`).text());

const pinArticle = (id: string) =>
  runCatching(client.put(`article/${id}/pinned`).text());

const unpinArticle = (id: string) =>
  runCatching(client.delete(`article/${id}/pinned`).text());

const lockArticle = (id: string) =>
  runCatching(client.put(`article/${id}/locked`).text());

const unlockArticle = (id: string) =>
  runCatching(client.delete(`article/${id}/locked`).text());

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
