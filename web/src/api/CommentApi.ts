import { createForumApi } from '@novelia/web-kit';

import { forumApiUrl } from '@/config';
import { webKit } from '@/webKit';

const client = webKit.createClient(forumApiUrl, { timeout: 60_000 });

export const CommentApi = createForumApi({
  client,
  url: forumApiUrl,
  type: 'novel',
});
