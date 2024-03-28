import { Comment1 } from '@/model/Comment';
import { Page } from '@/model/Page';

import { client } from './client';

const listComment = (params: {
  site: string;
  page: number;
  parentId?: string;
  pageSize: number;
}) => client.get('comment', { searchParams: params }).json<Page<Comment1>>();

const createComment = (json: {
  site: string;
  parent: string | undefined;
  content: string;
}) => client.post('comment', { json });

const hideComment = (id: string) => client.put(`comment/${id}/hidden`);
const unhideComment = (id: string) => client.delete(`comment/${id}/hidden`);

export const CommentRepository = {
  listComment,
  createComment,

  hideComment,
  unhideComment,
};
