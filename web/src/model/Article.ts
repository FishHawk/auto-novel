import { UserReference } from "./User";

export interface ArticleOutline {
  id: string;
  title: string;
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
  locked: boolean;
  pinned: boolean;
  numViews: number;
  numComments: number;
  user: UserReference;
  createAt: number;
  updateAt: number;
}
