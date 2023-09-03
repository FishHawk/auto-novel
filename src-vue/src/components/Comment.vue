<script lang="ts" setup>
import {
  KeyboardArrowUpFilled,
  KeyboardArrowDownFilled,
  CommentFilled,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import ApiComment, { SubCommentDto } from '@/data/api/api_comment';
import { useAuthInfoStore } from '@/data/stores/authInfo';

const { postId, parentId, comment } = withDefaults(
  defineProps<{
    postId: string;
    parentId: string | undefined;
    comment: SubCommentDto;
  }>(),
  { parentId: undefined }
);

const emit = defineEmits<{ replied: [] }>();

const authInfoStore = useAuthInfoStore();
const message = useMessage();

async function vote(isUpvote: boolean, isCancel: boolean) {
  const token = authInfoStore.token;
  if (token) {
    const result = await ApiComment.vote(comment.id, isUpvote, isCancel);
    if (result.ok) {
      if (isUpvote && isCancel) {
        comment.upvote--;
        comment.viewerVote = undefined;
      } else if (isUpvote && !isCancel) {
        if (comment.viewerVote === false) {
          comment.upvote++;
        }
        comment.upvote++;
        comment.viewerVote = true;
      } else if (!isUpvote && isCancel) {
        comment.downvote--;
        comment.viewerVote = undefined;
      } else {
        if (comment.viewerVote === true) {
          comment.upvote--;
        }
        comment.downvote++;
        comment.viewerVote = false;
      }
    } else {
      message.error('评论投票错误：' + result.error.message);
    }
  } else {
    message.info('请先登录');
  }
}

const showInput = ref(false);
const replyContent = ref('');

function replyClicked() {
  const token = authInfoStore.token;
  if (token) {
    showInput.value = !showInput.value;
  } else {
    message.info('请先登录');
  }
}

async function reply() {
  const token = authInfoStore.token;
  if (token) {
    if (replyContent.value.length === 0) {
      message.info('回复内容不能为空');
    } else {
      const receiver = parentId === undefined ? undefined : comment.username;
      const result = await ApiComment.reply(
        postId,
        parentId ?? comment.id,
        receiver,
        replyContent.value
      );
      if (result.ok) {
        emit('replied');
        replyContent.value = '';
        showInput.value = false;
      } else {
        message.error('回复加载错误：' + result.error.message);
      }
    }
  } else {
    message.info('请先登录');
  }
}
</script>

<template>
  <n-space align="center">
    <n-text style="font-weight: 900">{{ comment.username }}</n-text>
    <template v-if="comment.receiver">
      <n-text style="color: #7c7c7c">></n-text>
      <n-text style="font-weight: 900">{{ comment.receiver }}</n-text>
    </template>
    <n-text style="color: #7c7c7c">
      <n-time :time="comment.createAt * 1000" type="relative" />
    </n-text>
  </n-space>

  <div style="margin-top: 8px; margin-bottom: 4px">{{ comment.content }}</div>

  <n-space align="center">
    <n-button
      quaternary
      :type="comment.viewerVote === true ? 'primary' : 'tertiary'"
      @click="vote(true, comment.viewerVote === true)"
      style="padding: 4px"
    >
      <template #icon>
        <n-icon :size="24">
          <KeyboardArrowUpFilled />
        </n-icon>
      </template>
    </n-button>

    <div style="color: #7c7c7c; font-weight: 900">
      {{ comment.upvote - comment.downvote }}
    </div>

    <n-button
      quaternary
      :type="comment.viewerVote === false ? 'primary' : 'tertiary'"
      @click="vote(false, comment.viewerVote === false)"
      style="padding: 4px"
    >
      <template #icon>
        <n-icon :size="24">
          <KeyboardArrowDownFilled />
        </n-icon>
      </template>
    </n-button>

    <n-button quaternary type="tertiary" @click="replyClicked()">
      <template #icon>
        <n-icon>
          <CommentFilled />
        </n-icon>
      </template>
      回复
    </n-button>
  </n-space>

  <template v-if="showInput">
    <n-input
      v-model:value="replyContent"
      :placeholder="`回复${comment.username}`"
      type="textarea"
      style="margin-top: 10px"
    />
    <n-button type="primary" @click="reply()" style="margin-top: 10px">
      发布
    </n-button>
  </template>
</template>
