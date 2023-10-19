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

const list = (params: {
  site: string;
  page: number;
  parentId?: string;
  pageSize: number;
}) =>
  runCatching(
    client.get('comment', { searchParams: params }).json<Page<Comment1>>()
  );

const reply = (site: string, parent: string | undefined, content: string) =>
  runCatching(
    client
      .post('comment', {
        json: {
          site,
          parent,
          content,
        },
      })
      .text()
  );

export const ApiComment = {
  list,
  reply,
};
