<script lang="ts" setup>
import {
  CommentOutlined,
  DeleteOutlined,
  MoreVertOutlined,
} from '@vicons/material';

import type { Comment } from '@novelia/forum-api';
import { copyToClipboard, Toast } from '@novelia/web-kit';

import { useWhoamiStore } from '@/stores';
import { CommentRepo } from '@/repos';

const props = defineProps<{
  site: string;
  comment: Comment;
  canReply: boolean;
}>();

const emit = defineEmits<{
  reply: [Comment];
}>();

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const options = computed(() => {
  const options = [{ label: '复制', key: 'copy' }];
  if (whoami.value.asAdmin) {
    if (props.comment.status === 1) {
      options.push({ label: '解除隐藏', key: 'unhide' });
    } else if (props.comment.status === 0) {
      options.push({ label: '隐藏', key: 'hide' });
    }
  }
  if (whoami.value.user && whoami.value.user.id !== props.comment.authorId) {
    if (props.comment.authorBlocked) {
      options.push({ label: '解除屏蔽', key: 'unblock' });
    } else {
      options.push({ label: '屏蔽用户', key: 'block' });
    }
  }
  return options;
});

const handleSelect = (key: string) => {
  if (key === 'copy') {
    copyComment(props.comment);
  } else if (key === 'hide') {
    hideComment(props.comment);
  } else if (key === 'unhide') {
    unhideComment(props.comment);
  } else if (key === 'block') {
    blockUser(props.comment);
  } else if (key === 'unblock') {
    unblockUser(props.comment);
  }
};

function deleteComment() {
  Toast.promise(CommentRepo.deleteComment(props.comment.id, props.site), {
    success: '删除成功',
    error: '删除失败',
  });
}

function copyComment(comment: Comment) {
  Toast.promise(copyToClipboard(comment.content), {
    success: '复制成功',
    error: '复制失败',
  });
}

function hideComment(comment: Comment) {
  Toast.promise(CommentRepo.hideComment(comment.id), {
    success: '隐藏成功',
    error: '隐藏失败',
  });
}

function unhideComment(comment: Comment) {
  Toast.promise(CommentRepo.unhideComment(comment.id), {
    success: '解除隐藏成功',
    error: '解除隐藏失败',
  });
}

function blockUser(comment: Comment) {
  Toast.promise(CommentRepo.setUserBlocked(comment.authorId, true), {
    success: '屏蔽用户成功',
    error: '屏蔽用户失败',
  });
}

function unblockUser(comment: Comment) {
  Toast.promise(CommentRepo.setUserBlocked(comment.authorId, false), {
    success: '解除屏蔽用户成功',
    error: '解除屏蔽用户失败',
  });
}

const isDeletable = computed(() => {
  return (
    whoami.value.asAdmin ||
    (whoami.value.user?.id === props.comment.authorId &&
      Date.now() - new Date(props.comment.createdAt).getTime() < 20 * 60_000)
  );
});
</script>

<template>
  <article class="min-w-0 py-4">
    <header class="flex items-center gap-2 text-xs text-muted">
      <span class="min-w-0 break-words text-sm font-bold text-ink">
        {{ comment.authorUsername }}
      </span>
      <n-time
        class="shrink-0"
        :time="new Date(comment.createdAt).getTime()"
        type="relative"
      />

      <div class="ml-auto flex shrink-0 items-center gap-1">
        <c-button
          v-if="comment.rootId === null && canReply"
          class="comment-button comment-button--ghost comment-button--compact"
          label="回复"
          :icon="CommentOutlined"
          require-login
          :round="false"
          quaternary
          type="tertiary"
          size="tiny"
          @action="emit('reply', comment)"
        />

        <c-button-confirm
          v-if="isDeletable"
          class="comment-button comment-button--ghost comment-button--compact"
          hint="真的要删除评论吗？"
          label="删除"
          :icon="DeleteOutlined"
          require-login
          :round="false"
          quaternary
          type="tertiary"
          size="tiny"
          @action="deleteComment()"
        />

        <n-dropdown trigger="click" :options="options" @select="handleSelect">
          <n-button
            class="comment-button comment-button--ghost comment-button--icon"
            quaternary
            type="tertiary"
            size="tiny"
            aria-label="更多操作"
            title="更多操作"
          >
            <n-icon :component="MoreVertOutlined" :size="16" />
          </n-button>
        </n-dropdown>
      </div>
    </header>

    <div class="comment-body mt-0.5 rounded-floating px-4 py-3">
      <p v-if="comment.status !== 0" class="text-sm leading-6 text-muted">
        {{ comment.status === 1 ? '[隐藏]' : '[删除]' }}
      </p>
      <p v-else-if="comment.authorBlocked" class="text-sm leading-6 text-muted">
        [屏蔽]
      </p>
      <MarkdownView v-else mode="comment" :source="comment.content" />
    </div>
  </article>
</template>
