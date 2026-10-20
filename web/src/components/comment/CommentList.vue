<script lang="ts" setup>
import { CommentOutlined } from '@vicons/material';
import { XAsyncContent } from '@novelia/web-kit';

import { CommentRepo } from '@/repos';
import type { Comment } from '@novelia/forum-api';
import { useDraftStore, useWhoamiStore } from '@/stores';

const props = defineProps<{
  site: string;
  locked: boolean;
}>();

const page = ref(1);
const { data: commentPage, error } = CommentRepo.useCommentList(
  page,
  () => props.site,
);

const whoamiStore = useWhoamiStore();
const { whoami } = storeToRefs(whoamiStore);

const draftStore = useDraftStore();
const draftId = `comment-${props.site}`;

const anchorEl = useTemplateRef('anchor');
watch(page, () => {
  anchorEl.value?.scrollIntoView();
  window.scrollBy({ top: -50, behavior: 'auto' });
});

const showInput = ref(false);
const replyTo = ref<Comment>();

function onReplied() {
  showInput.value = false;
  replyTo.value = undefined;
  draftStore.cancelAddDraft();
  draftStore.removeDraft(draftId);
}

function toggleInput() {
  showInput.value = !showInput.value;
  replyTo.value = undefined;
}

function toggleReply(comment: Comment) {
  replyTo.value = replyTo.value?.id === comment.id ? undefined : comment;
  showInput.value = false;
}

function cancelInput() {
  showInput.value = false;
  replyTo.value = undefined;
}

const canReply = computed(() => {
  return whoami.value.hasNovelAccess && !props.locked;
});
</script>

<template>
  <div ref="anchor" />
  <section class="comment-list mt-5 border-t border-divider">
    <header
      class="flex items-center justify-between gap-4 py-4"
      :class="{ 'border-b border-divider': !showInput }"
    >
      <h2 class="text-lg font-semibold text-ink">评论</h2>
      <c-button
        v-if="canReply"
        class="comment-button comment-button--primary comment-button--small"
        label="发表评论"
        :icon="CommentOutlined"
        require-login
        :round="false"
        type="primary"
        @action="toggleInput()"
      />
    </header>

    <p v-if="locked" class="py-2 text-sm text-warning">
      评论区已锁定，不能再回复。
    </p>

    <CommentEditor
      v-if="showInput"
      class="border-b border-divider py-4"
      :site="site"
      :draft-id="draftId"
      placeholder="发表评论"
      @replied="onReplied()"
      @cancel="cancelInput()"
    />

    <CPage
      v-model:page="page"
      :page-size="10"
      :total="commentPage?.total"
      disable-top
    >
      <template v-if="commentPage">
        <div class="divide-y divide-divider">
          <CommentThread
            v-for="comment in commentPage.items"
            :key="comment.id"
            :site="site"
            :comment="comment"
            :can-reply="canReply"
            @reply="toggleReply"
          >
            <CommentEditor
              v-if="replyTo?.id === comment.id"
              class="mt-3 pb-4"
              :site="site"
              :draft-id="draftId"
              :parent="comment.id"
              :placeholder="`回复${comment.authorUsername}`"
              @replied="onReplied()"
              @cancel="cancelInput()"
            />
          </CommentThread>
        </div>
        <div
          v-if="commentPage.items.length === 0 && !locked"
          class="grid min-h-52 place-items-center p-6 text-center"
        >
          <div>
            <div
              class="mx-auto grid size-11 place-items-center rounded-full bg-paper text-muted"
              aria-hidden="true"
            >
              <CommentOutlined class="size-5" />
            </div>
            <p class="mt-3 text-sm font-medium text-ink">暂无评论</p>
          </div>
        </div>
      </template>

      <XAsyncContent
        v-else
        :loading="!error"
        :error="error?.message ?? ''"
        error-title="加载错误"
      />
    </CPage>
  </section>
</template>

<style scoped>
.comment-list {
  --comment-background: #fafafc;
  --markdown-font-size: 0.875rem;
  --markdown-line-height: 1.6;
  --markdown-star-color: rgb(219, 219, 223);
  --markdown-star-active-color: #4fb233;
  --markdown-spoiler-background: #1f2225;
  --markdown-spoiler-color: #fff;
}

:global(:root[data-theme='dark']) .comment-list {
  --comment-background: rgb(255 255 255 / 6%);
  --markdown-spoiler-background: rgb(255 255 255 / 20%);
  --markdown-spoiler-color: #f2f2f5;
}

.comment-list :deep(.comment-body) {
  background-color: var(--comment-background);
}

/* 保留原按钮的登录、加载和确认逻辑，仅对齐 forum 的按钮外观。 */
.comment-list :deep(.comment-button) {
  height: 2.5rem;
  padding: 0 1rem;
  border-radius: var(--radius-sm);
  font-size: 0.875rem;
  font-weight: 500;
}

.comment-list :deep(.comment-button--small) {
  height: 2.25rem;
  padding: 0 0.75rem;
}

.comment-list :deep(.comment-button--compact) {
  height: 2rem;
  padding: 0 0.5rem;
  font-size: 0.75rem;
  font-weight: 600;
}

.comment-list :deep(.comment-button--icon) {
  width: 1.5rem;
  height: 1.5rem;
  padding: 0;
}

.comment-list :deep(.comment-button--ghost),
.comment-list :deep(.comment-button--outline) {
  color: var(--color-muted);
  background-color: transparent;
}

.comment-list :deep(.comment-button--ghost:hover:not(:disabled)) {
  color: var(--color-primary);
  background-color: var(--color-paper);
}

.comment-list :deep(.comment-button--outline:hover:not(:disabled)) {
  color: var(--color-primary);
}

.comment-list :deep(.comment-button--primary) {
  color: var(--color-on-primary);
  background-color: var(--color-primary);
}

.comment-list :deep(.comment-button--primary:hover:not(:disabled)) {
  background-color: var(--color-primary-hover);
}

.comment-list :deep(.n-pagination) {
  padding: 0.75rem 1rem;
  border-top: 1px solid var(--color-divider);
}
</style>
