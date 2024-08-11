import { MongoClient } from 'mongodb';

const client = new MongoClient('mongodb://localhost:27017/main');
const db = client.db('main');

export const MONGO = {
  client,
  db,

  ARTICLE: 'article',
  COMMENT: 'comment-alt',
  GLOSSARY: 'glossary',
  OPERATION_HISTORY: 'operation-history',
  SAKURA_WEB_INCORRECT_CASE: 'sakura-incorrect-case',
  USER: 'user',

  WEB_NOVEL: 'metadata',
  WEB_FAVORITE: 'web-favorite',
  WEB_READ_HISTORY: 'web-read-history',

  WENKU_NOVEL: 'wenku-metadata',
  WENKU_FAVORITE: 'wenku-favorite',

  WEB_CHAPTER: 'episode',
  TOC_MERGE_HISTORY: 'toc-merge-history',

  col: (s: string) => db.collection(s),
};
