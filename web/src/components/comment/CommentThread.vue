<script lang="ts" setup>
import { XAsyncContent } from '@novelia/web-kit';

import type { Comment } from '@novelia/forum-api';
import { CommentRepo } from '@/repos';

const props = defineProps<{
  site: string;
  comment: Comment;
  canReply: boolean;
  replyToId?: number;
}>();

const emit = defineEmits<{
  reply: [Comment];
  cancelReply: [];
}>();

const page = ref(1);
const { data: replyPage, error } = CommentRepo.useReplyList(
  page,
  () => props.site,
  () => props.comment.id,
  () => props.comment.replyCount > 0,
);
const pageCount = computed(() =>
  Math.ceil(
    (replyPage.value?.total ?? props.comment.replyCount) /
      CommentRepo.replyPageSize,
  ),
);
</script>

<template>
  <section class="min-w-0">
    <CommentItem
      :site="site"
      :comment="comment"
      :can-reply="canReply"
      :replying="replyToId === comment.id"
      @reply="emit('reply', $event)"
      @cancel-reply="emit('cancelReply')"
    >
      <slot v-if="replyToId === comment.id" />
    </CommentItem>

    <div v-if="comment.replyCount > 0" class="border-t border-divider/60">
      <template v-if="replyPage">
        <CommentItem
          v-for="reply in replyPage.items"
          :key="reply.id"
          class="ml-8"
          :site="site"
          :comment="reply"
          :can-reply="canReply"
          :replying="replyToId === reply.id"
          @reply="emit('reply', $event)"
          @cancel-reply="emit('cancelReply')"
        >
          <slot v-if="replyToId === reply.id" />
        </CommentItem>
        <n-pagination
          v-if="pageCount > 1"
          v-model:page="page"
          :page-count="pageCount"
          :page-slot="7"
        />
      </template>
      <XAsyncContent
        v-else
        class="py-4 pl-8"
        :loading="!error"
        :error="error?.message ?? ''"
        error-title="回复加载错误"
      />
    </div>
  </section>
</template>
