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

export const CommentRepository = {
  listComment,
  createComment,
};
