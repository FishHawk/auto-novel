import { Options } from 'ky';
import api from './api';
import { Result, runCatching } from './result';

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

async function list(
  postId: string,
  page: number,
  token: string | undefined
): Promise<Result<CommentPageDto>> {
  const options: Options = {
    searchParams: { postId, page },
  };
  if (token) {
    options.headers = {
      Authorization: 'Bearer ' + token,
    };
  }
  return runCatching(api.get(`comment/list`, options).json());
}

async function listSub(
  postId: string,
  parentId: string,
  page: number,
  token: string | undefined
): Promise<Result<SubCommentPageDto>> {
  const options: Options = {
    searchParams: { postId, parentId, page },
  };
  if (token) {
    options.headers = {
      Authorization: 'Bearer ' + token,
    };
  }
  return runCatching(
    api.get(`comment/list-sub`, options).json()
  );
}

async function vote(
  commentId: string,
  isUpvote: boolean,
  isCancel: boolean,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .post('comment/vote', {
        headers: {
          Authorization: 'Bearer ' + token,
        },
        searchParams: {
          commentId,
          isUpvote,
          isCancel,
        },
      })
      .text()
  );
}

async function reply(
  postId: string,
  parentId: string | undefined,
  receiver: string | undefined,
  content: string,
  token: string
): Promise<Result<string>> {
  return runCatching(
    api
      .post('comment', {
        headers: {
          Authorization: 'Bearer ' + token,
        },
        json: {
          postId,
          parentId,
          receiver,
          content,
        },
      })
      .text()
  );
}

export default {
  list,
  listSub,
  vote,
  reply,
};
