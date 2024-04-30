<script lang="ts" setup>
import { CommentOutlined } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';

import { Locator } from '@/data';
import { CommentRepository } from '@/data/api';
import { Comment1 } from '@/model/Comment';
import { runCatching } from '@/util/result';

import { doAction } from '../util';

const [DefineCommentContent, ReuseCommentContent] = createReusableTemplate<{
  comment: Comment1;
  reply: boolean;
}>();

const { site, comment } = defineProps<{
  site: string;
  comment: Comment1;
  locked: boolean;
}>();

const message = useMessage();
const asAdmin = Locator.userDataRepository().asAdmin;

const currentPage = ref(1);
const pageCount = ref(Math.floor((comment.numReplies + 9) / 10));

const loadReplies = async (page: number) => {
  const result = await runCatching(
    CommentRepository.listComment({
      site,
      parentId: comment.id,
      page: page - 1,
      pageSize: 10,
    })
  );
  if (result.ok) {
    pageCount.value = result.value.pageNumber;
    comment.replies = result.value.items;
  } else {
    message.error('回复加载错误：' + result.error.message);
  }
};

watch(currentPage, async (page) => loadReplies(page));

function onReplied() {
  showInput.value = false;
  if (currentPage >= pageCount) {
    loadReplies(currentPage.value);
  }
}

const hideComment = (comment: Comment1) =>
  doAction(
    CommentRepository.hideComment(comment.id).then(
      () => (comment.hidden = true)
    ),
    '隐藏',
    message
  );

const unhideComment = (comment: Comment1) =>
  doAction(
    CommentRepository.unhideComment(comment.id).then(
      () => (comment.hidden = false)
    ),
    '解除隐藏',
    message
  );

const showInput = ref(false);

const splitByLinks = (text: string): [string, boolean][] => {
  const regExp =
    /((?:http|https|ftp|ftps)\:\/\/[a-zA-Z0-9\-\.]+\.[a-zA-Z]{2,3}(?:\/\S*)?)/g;
  return text.split(regExp).map((part) => [part, regExp.test(part)]);
};
</script>

<template>
  <DefineCommentContent v-slot="{ comment, reply }">
    <n-flex align="center">
      <n-text>
        <b>{{ comment.user.username }}</b>
      </n-text>
      <n-text depth="3" style="font-size: 12px">
        <n-time :time="comment.createAt * 1000" type="relative" />
      </n-text>

      <c-button
        v-if="reply"
        label="回复"
        :icon="CommentOutlined"
        require-login
        quaternary
        type="tertiary"
        size="tiny"
        @action="showInput = !showInput"
      />

      <template v-if="asAdmin">
        <c-button
          v-if="comment.hidden"
          label="解除隐藏"
          quaternary
          type="tertiary"
          size="tiny"
          @action="unhideComment(comment)"
        />
        <c-button
          v-else
          label="隐藏"
          quaternary
          type="tertiary"
          size="tiny"
          @action="hideComment(comment)"
        />
      </template>
    </n-flex>

    <n-card embedded :bordered="false" size="small" style="margin-top: 2px">
      <n-p style="white-space: pre-wrap">
        <n-text v-if="comment.hidden" depth="3">[隐藏] </n-text>
        <template v-for="[part, isLink] in splitByLinks(comment.content)">
          <n-a v-if="isLink" :href="part" target="_blank">{{ part }}</n-a>
          <template v-else>{{ part }}</template>
        </template>
      </n-p>
    </n-card>
  </DefineCommentContent>

  <div ref="topElement" />
  <ReuseCommentContent :comment="comment" :reply="!locked" />

  <CommentInput
    v-if="showInput"
    :site="site"
    :parent="comment.id"
    :placeholder="`回复${comment.user.username}`"
    @replied="onReplied()"
  />

  <div
    v-for="replyComment in comment.replies"
    style="margin-left: 30px; margin-top: 20px"
  >
    <ReuseCommentContent :comment="replyComment" :reply="false" />
  </div>

  <n-pagination
    v-if="comment.numReplies > 10"
    v-model:page="currentPage"
    :page-count="pageCount"
    :page-slot="7"
    style="margin-left: 30px; margin-top: 20px"
  />
</template>
