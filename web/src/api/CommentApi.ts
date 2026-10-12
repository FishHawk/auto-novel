import { createForumApi } from '@novelia/forum-api';

import { forumApiUrl } from '@/config';
import { webKit } from '@/main';

const client = webKit.createClient(forumApiUrl, { timeout: 60_000 });

export const CommentApi = createForumApi({
  client,
  url: forumApiUrl,
  type: 'novel',
});
