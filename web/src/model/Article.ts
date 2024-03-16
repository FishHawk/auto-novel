import { UserReference } from './User';

export type ArticleCategory = 'Guide' | 'General' | 'Support';

export interface ArticleOutline {
  id: string;
  title: string;
  category: ArticleCategory;
  locked: boolean;
  pinned: boolean;
  numViews: number;
  numComments: number;
  user: UserReference;
  createAt: number;
  updateAt: number;
}

export interface Article {
  id: string;
  title: string;
  content: string;
  category: ArticleCategory;
  locked: boolean;
  pinned: boolean;
  numViews: number;
  numComments: number;
  user: UserReference;
  createAt: number;
  updateAt: number;
}
