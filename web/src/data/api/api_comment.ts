import { runCatching } from '@/data/result';

import { client } from './client';
import { Page, UserOutline } from './common';

export interface Comment1 {
  id: string;
  user: UserOutline;
  content: string;
  createAt: number;
  numReplies: number;
  replies: Comment1[];
}

const listComment = (params: {
  site: string;
  page: number;
  parentId?: string;
  pageSize: number;
}) =>
  runCatching(
    client.get('comment', { searchParams: params }).json<Page<Comment1>>()
  );

const createComment = (json: {
  site: string;
  parent: string | undefined;
  content: string;
}) => runCatching(client.post('comment', { json }).text());

export const ApiComment = {
  listComment,
  createComment,
};
