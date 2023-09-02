import { api } from './api';
import { runCatching } from './result';

interface BaseCommentDto {
  id: string;
  createAt: number;
  username: string;
  upvote: number;
  downvote: number;
  viewerVote?: boolean;
  content: string;
}

export type SubCommentDto = BaseCommentDto & { receiver?: string };

export type CommentDto = BaseCommentDto & {
  pageNumber: number;
  items: SubCommentDto[];
};

export interface SubCommentPageDto {
  pageNumber: number;
  items: SubCommentDto[];
}

export interface CommentPageDto {
  pageNumber: number;
  items: CommentDto[];
}

const list = (postId: string, page: number) =>
  runCatching(
    api
      .get(`comment/list`, { searchParams: { postId, page } })
      .json<CommentPageDto>()
  );

const listSub = (postId: string, parentId: string, page: number) =>
  runCatching(
    api
      .get(`comment/list-sub`, { searchParams: { postId, parentId, page } })
      .json<SubCommentPageDto>()
  );

const vote = (commentId: string, isUpvote: boolean, isCancel: boolean) =>
  runCatching(
    api
      .post('comment/vote', {
        searchParams: {
          commentId,
          isUpvote,
          isCancel,
        },
      })
      .text()
  );

const reply = (
  postId: string,
  parentId: string | undefined,
  receiver: string | undefined,
  content: string
) =>
  runCatching(
    api
      .post('comment', {
        json: {
          postId,
          parentId,
          receiver,
          content,
        },
      })
      .text()
  );

export default {
  list,
  listSub,
  vote,
  reply,
};
