<script lang="ts" setup>
import {
  CommentOutlined,
  DeleteOutlined,
  MoreVertOutlined,
} from '@vicons/material';

import type { Comment } from '@novelia/forum-api';
import { Toast } from '@novelia/web-kit';

import { useWhoamiStore } from '@/stores';
import { CommentRepo } from '@/repos';
import { copyToClipBoard } from '@/pages/util';

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
  Toast.promise(copyToClipBoard(comment.content), {
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
  <n-flex align="center" :size="0">
    <n-text>
      <b>{{ comment.authorUsername }}</b>
    </n-text>
    <n-text depth="3" style="font-size: 12px; margin-left: 12px">
      <n-time :time="new Date(comment.createdAt).getTime()" type="relative" />
    </n-text>

    <div style="flex: 1" />

    <c-button
      v-if="comment.rootId === null && canReply"
      label="回复"
      :icon="CommentOutlined"
      require-login
      quaternary
      type="tertiary"
      size="tiny"
      style="margin-right: 4px"
      @action="emit('reply', comment)"
    />

    <c-button-confirm
      v-if="isDeletable"
      hint="真的要删除评论吗？"
      label="删除"
      :icon="DeleteOutlined"
      require-login
      quaternary
      type="tertiary"
      size="tiny"
      style="margin-right: 4px"
      @action="deleteComment()"
    />

    <n-dropdown trigger="click" :options="options" @select="handleSelect">
      <n-button circle quaternary type="tertiary" size="tiny">
        <n-icon :component="MoreVertOutlined" />
      </n-button>
    </n-dropdown>
  </n-flex>

  <n-card embedded :bordered="false" size="small" style="margin-top: 2px">
    <n-text v-if="comment.status !== 0" depth="3">
      {{ comment.status === 1 ? '[隐藏]' : '[删除]' }}
    </n-text>
    <n-text v-else-if="comment.authorBlocked" depth="3">[屏蔽]</n-text>
    <MarkdownView
      v-else
      mode="comment"
      :source="comment.content"
      style="margin-top: -1em; margin-bottom: -1em"
    />
  </n-card>
</template>
