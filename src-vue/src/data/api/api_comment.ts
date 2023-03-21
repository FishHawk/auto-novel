import { Options } from 'ky';
import api from './api';
import { Err, Ok, Result } from './result';

function dn(i: number) {
  return Math.round(Math.random() * i);
}
function ds(i: number) {
  return (Math.random() + 1).toString(36);
}
function dbc(): BaseCommentDto {
  return {
    id: ds(4),
    createAt: Date.now(),
    username: ds(4),
    upvote: dn(100),
    downvote: dn(100),
    viewerVote: dn(1) > 0.5,
    content: ds(30),
  };
}
function dsc(): SubCommentDto {
  return { ...dbc(), receiver: ds(4) };
}
function dtc(): CommentDto {
  if (Math.random() > 0.7) {
    return {
      ...dbc(),
      pageNumber: 3,
      items: [dsc(), dsc(), dsc(), dsc()],
    };
  } else {
    return {
      ...dbc(),
      pageNumber: 0,
      items: [],
    };
  }
}
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
  return api
    .get(`comment/list`, options)
    .json<CommentPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
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
  return api
    .get(`comment/list-sub`, options)
    .json<CommentPageDto>()
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function vote(
  commentId: string,
  isUpvote: boolean,
  isCancel: boolean,
  token: string
): Promise<Result<string>> {
  return api
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
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

async function reply(
  postId: string,
  parentId: string | undefined,
  receiver: string | undefined,
  content: string,
  token: string
): Promise<Result<string>> {
  return api
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
    .then((it) => Ok(it))
    .catch((error) => Err(error));
}

export default {
  list,
  listSub,
  vote,
  reply,
};
