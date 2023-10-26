import { runCatching } from '@/data/result';

import { UserReference } from './api_user';
import { client } from './client';
import { Page } from './common';

export interface Comment1 {
  id: string;
  user: UserReference;
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
