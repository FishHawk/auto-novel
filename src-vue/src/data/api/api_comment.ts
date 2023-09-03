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

const list = (site: string, page: number) =>
  runCatching(
    api
      .get(`comment/list`, { searchParams: { site, page } })
      .json<Page<Comment1>>()
  );

const listSub = (site: string, parent: string, page: number) =>
  runCatching(
    api
      .get(`comment/list-sub`, {
        searchParams: { site, parentId: parent, page },
      })
      .json<Page<Comment1>>()
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
  listSub,
  reply,
};
