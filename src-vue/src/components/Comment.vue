<script lang="ts" setup>
import {
  KeyboardArrowUpFilled,
  KeyboardArrowDownFilled,
  CommentFilled,
} from '@vicons/material';
import { useMessage } from 'naive-ui';
import { ref } from 'vue';

import ApiComment, { SubCommentDto } from '../data/api/api_comment';
import { useAuthInfoStore } from '../data/stores/authInfo';
import { errorToString } from '../data/handle_error';

const authInfoStore = useAuthInfoStore();

const props = defineProps<{
  postId: string;
  parentId: string | undefined;
  comment: SubCommentDto;
  showInput: boolean;
}>();

const emit = defineEmits<{
  (e: 'replied'): void;
}>();

const message = useMessage();

async function vote(isUpvote: boolean, isCancel: boolean) {
  const token = authInfoStore.token;
  if (token) {
    const result = await ApiComment.vote(
      props.comment.id,
      isUpvote,
      isCancel,
      token
    );
    if (result.ok) {
      if (isUpvote && isCancel) {
        props.comment.upvote--;
        props.comment.viewerVote = undefined;
      } else if (isUpvote && !isCancel) {
        if (props.comment.viewerVote === false) {
          props.comment.upvote++;
        }
        props.comment.upvote++;
        props.comment.viewerVote = true;
      } else if (!isUpvote && isCancel) {
        props.comment.downvote--;
        props.comment.viewerVote = undefined;
      } else {
        if (props.comment.viewerVote === true) {
          props.comment.upvote--;
        }
        props.comment.downvote++;
        props.comment.viewerVote = false;
      }
    } else {
      message.error('评论投票错误：' + errorToString(result.error));
    }
  } else {
    message.info('请先登录');
  }
}

function readableDate(timestamp: number) {
  const diff = Math.abs(Date.now() / 1000 - timestamp);
  if (diff < 60) {
    return '刚刚';
  } else if (diff < 3600) {
    return `${Math.floor(diff / 60)}分钟前`;
  } else if (diff < 86400) {
    return `${Math.floor(diff / 3600)}小时前`;
  } else {
    const date = new Date(timestamp);
    const year = date.getFullYear();
    var month = (1 + date.getMonth()).toString();
    month = month.length > 1 ? month : '0' + month;
    var day = date.getDate().toString();
    day = day.length > 1 ? day : '0' + day;
    if (new Date().getFullYear() === year) {
      return `${month}-${day}`;
    } else {
      return `${year}-${month}-${day}`;
    }
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
      const receiver =
        props.parentId === undefined ? undefined : props.comment.username;
      const parentId = props.parentId ?? props.comment.id;
      const result = await ApiComment.reply(
        props.postId,
        parentId,
        receiver,
        replyContent.value,
        token
      );
      if (result.ok) {
        emit('replied');
        replyContent.value = '';
        showInput.value = false;
      } else {
        message.error('回复加载错误：' + errorToString(result.error));
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
    <n-text style="color: #7c7c7c">{{ readableDate(comment.createAt) }}</n-text>
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
