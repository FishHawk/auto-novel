import { api } from './api';
import { UserOutline } from './api_article';
import { Page } from './page';
import { runCatching } from './result';

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
    api.get('comment', { searchParams: params }).json<Page<Comment1>>()
  );

const reply = (site: string, parent: string | undefined, content: string) =>
  runCatching(
    api
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
