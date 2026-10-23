import type { Comment } from '@novelia/web-kit';

export const COMMENT_EDIT_WINDOW = 20 * 60_000;

export function getCommentPermissions(
  comment: Pick<Comment, 'authorId' | 'status' | 'createdAt'>,
  viewer: { userId?: number; asAdmin: boolean; canReply: boolean },
  now: number,
) {
  const signedIn = viewer.userId !== undefined;
  const owner = signedIn && viewer.userId === comment.authorId;
  const published = comment.status === 0;
  const deadline = new Date(comment.createdAt).getTime() + COMMENT_EDIT_WINDOW;
  const canEdit =
    signedIn && published && (viewer.asAdmin || (owner && now <= deadline));
  return {
    canReply: signedIn && published && viewer.canReply,
    canEdit,
    canDelete: signedIn && (viewer.asAdmin || canEdit),
    canBlockAuthor: signedIn && !owner,
    canModerateAuthor: signedIn && viewer.asAdmin && !owner,
    showActions:
      published || (signedIn && viewer.asAdmin && comment.status === 1),
  };
}
