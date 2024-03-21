<script lang="ts" setup>
import { CommentOutlined } from '@vicons/material';

import { CommentRepository } from '@/data/api';
import { Ok, Result, runCatching } from '@/pages/result';
import { Comment1 } from '@/model/Comment';
import { Page } from '@/model/Page';

const { site } = withDefaults(
  defineProps<{
    site: string;
    locked: boolean;
  }>(),
  { locked: false }
);

const commentPage = ref<Result<Page<Comment1>>>();
const currentPage = ref(1);

async function loadComments(page: number) {
  const result = await runCatching(
    CommentRepository.listComment({
      site,
      page: page - 1,
      pageSize: 10,
    })
  );
  if (result.ok) {
    commentPage.value = Ok({
      ...result.value,
      page,
      items: result.value.items.map((it) => ({ ...it, page: 1 })),
    });
  } else {
    commentPage.value = result;
  }
}

watch(currentPage, (page) => loadComments(page), { immediate: true });

function onReplied() {
  showInput.value = false;
  if (commentPage.value?.ok && currentPage.value === 1) {
    loadComments(currentPage.value);
  }
}

const showInput = ref(false);
</script>

<template>
  <section-header title="评论">
    <c-button
      v-if="!locked"
      label="发表评论"
      :icon="CommentOutlined"
      require-login
      @action="showInput = !showInput"
    />
  </section-header>

  <n-p v-if="locked">评论区已锁定，不能再回复。</n-p>

  <template v-if="showInput">
    <CommentInput
      :site="site"
      :placeholder="`发表回复`"
      @replied="onReplied()"
    />
    <n-divider />
  </template>

  <ResultView
    :result="commentPage"
    :showEmpty="(it: Page<Comment1>) => it.items.length === 0 && !locked"
    v-slot="{ value }"
  >
    <template v-for="comment in value.items">
      <Comment :site="site" :comment="comment" :locked="locked" />
      <n-divider />
    </template>

    <n-pagination
      v-if="value.pageNumber > 1"
      v-model:page="currentPage"
      :page-count="value.pageNumber"
      :page-slot="7"
      style="margin-top: 20px"
    />
  </ResultView>
</template>
