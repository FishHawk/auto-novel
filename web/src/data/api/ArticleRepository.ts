import { Article, ArticleCategory, ArticleSimplified } from '@/model/Article';
import { Page } from '@/model/Page';

import { client } from './client';

const listArticle = (params: {
  page: number;
  pageSize: number;
  category: ArticleCategory;
}) =>
  client
    .get('article', { searchParams: params })
    .json<Page<ArticleSimplified>>();

const getArticle = (id: string) => client.get(`article/${id}`).json<Article>();
const deleteArticle = (id: string) => client.delete(`article/${id}`);

interface ArticleBody {
  title: string;
  content: string;
  category: ArticleCategory;
}

const createArticle = (json: ArticleBody) =>
  client.post('article', { json }).text();

const updateArticle = (id: string, json: ArticleBody) =>
  client.put(`article/${id}`, { json }).text();

const pinArticle = (id: string) => client.put(`article/${id}/pinned`);
const unpinArticle = (id: string) => client.delete(`article/${id}/pinned`);

const lockArticle = (id: string) => client.put(`article/${id}/locked`);
const unlockArticle = (id: string) => client.delete(`article/${id}/locked`);

const hideArticle = (id: string) => client.put(`article/${id}/hidden`);
const unhideArticle = (id: string) => client.delete(`article/${id}/hidden`);

export const ArticleRepository = {
  listArticle,

  getArticle,
  createArticle,
  updateArticle,
  deleteArticle,

  pinArticle,
  unpinArticle,
  lockArticle,
  unlockArticle,
  hideArticle,
  unhideArticle,
};
