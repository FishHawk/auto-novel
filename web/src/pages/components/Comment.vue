<script lang="ts" setup>
import { CommentOutlined } from '@vicons/material';
import { createReusableTemplate } from '@vueuse/core';
import { useMessage } from 'naive-ui';
import { ref, watch } from 'vue';

import { CommentRepository } from '@/data/api';
import { Comment1 } from '@/model/Comment';
import { runCatching } from '@/pages/result';

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

const showInput = ref(false);
</script>

<template>
  <DefineCommentContent v-slot="{ comment, reply }">
    <n-flex align="center">
      <n-text>
        <b>{{ comment.user.username }}</b>
      </n-text>
      <n-text depth="3">
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
    </n-flex>
    <n-card embedded :bordered="false" size="small">
      <n-p style="white-space: pre-wrap">{{ comment.content }}</n-p>
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
