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
  <SectionHeader
    title="评论"
    ref="commentSectionRef"
    style="margin-bottom: 32px"
  >
    <c-button
      v-if="canReply"
      label="发表评论"
      :icon="CommentOutlined"
      require-login
      @action="toggleInput()"
    />
  </SectionHeader>

  <n-p v-if="locked">评论区已锁定，不能再回复。</n-p>

  <template v-if="showInput">
    <CommentEditor
      :site="site"
      :draft-id="draftId"
      placeholder="发表评论"
      @replied="onReplied()"
      @cancel="cancelInput()"
    />
    <n-divider />
  </template>

  <CPage
    v-model:page="page"
    :page-size="10"
    :total="commentPage?.total"
    disable-top
  >
    <template v-if="commentPage">
      <template v-for="comment in commentPage.items" :key="comment.id">
        <CommentThread
          :site="site"
          :comment="comment"
          :can-reply="canReply"
          @reply="toggleReply"
        >
          <CommentEditor
            v-if="replyTo?.id === comment.id"
            :site="site"
            :draft-id="draftId"
            :parent="comment.id"
            :placeholder="`回复${comment.authorUsername}`"
            style="padding-top: 8px"
            @replied="onReplied()"
            @cancel="cancelInput()"
          />
        </CommentThread>
        <n-divider />
      </template>
      <n-empty
        v-if="commentPage.items.length === 0 && !locked"
        description="暂无评论"
      />
    </template>

    <XAsyncContent
      v-else
      :loading="!error"
      :error="error?.message ?? ''"
      error-title="加载错误"
    />
  </CPage>
</template>
